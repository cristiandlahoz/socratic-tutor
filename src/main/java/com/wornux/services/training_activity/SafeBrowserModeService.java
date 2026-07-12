package com.wornux.services.training_activity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.SafeBrowserAlert;
import com.wornux.data.entities.training_activity.SafeBrowserAlertStatus;
import com.wornux.data.entities.training_activity.SafeBrowserEvent;
import com.wornux.data.entities.training_activity.SafeBrowserEventSeverity;
import com.wornux.data.entities.training_activity.SafeBrowserEventType;
import com.wornux.data.entities.training_activity.SafeBrowserSession;
import com.wornux.data.entities.training_activity.SafeBrowserSessionStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.training_activity.SafeBrowserAlertRepository;
import com.wornux.data.repositories.training_activity.SafeBrowserEventRepository;
import com.wornux.data.repositories.training_activity.SafeBrowserSessionRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SafeBrowserModeService {

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private static final Logger LOGGER = LoggerFactory.getLogger(SafeBrowserModeService.class);

    private final TrainingActivityAssignmentRepository assignmentRepository;
    private final TrainingActivityRepository activityRepository;
    private final SafeBrowserSessionRepository sessionRepository;
    private final SafeBrowserEventRepository eventRepository;
    private final SafeBrowserAlertRepository alertRepository;
    private final ActiveAcademicContextResolver contextResolver;
    private final SafeBrowserAssignmentStateBus assignmentStateBus;
    private final Counter sessionStartCounter;
    private final Counter heartbeatCounter;
    private final Counter duplicateConflictCounter;
    private final Timer sessionStartLatency;
    private final Timer heartbeatLatency;

    public SafeBrowserModeService(
            TrainingActivityAssignmentRepository assignmentRepository,
            TrainingActivityRepository activityRepository,
            SafeBrowserSessionRepository sessionRepository,
            SafeBrowserEventRepository eventRepository,
            SafeBrowserAlertRepository alertRepository,
            ActiveAcademicContextResolver contextResolver,
            SafeBrowserAssignmentStateBus assignmentStateBus,
            MeterRegistry meterRegistry) {
        this.assignmentRepository = assignmentRepository;
        this.activityRepository = activityRepository;
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.alertRepository = alertRepository;
        this.contextResolver = contextResolver;
        this.assignmentStateBus = assignmentStateBus;
        this.sessionStartCounter = meterRegistry.counter("training.activity.safe-browser.session.start");
        this.heartbeatCounter = meterRegistry.counter("training.activity.safe-browser.heartbeat");
        this.duplicateConflictCounter = meterRegistry.counter("training.activity.safe-browser.violation.duplicate");
        this.sessionStartLatency = meterRegistry.timer("training.activity.safe-browser.session.start.latency");
        this.heartbeatLatency = meterRegistry.timer("training.activity.safe-browser.heartbeat.latency");
    }

    @Transactional(readOnly = true)
    public List<SafeBrowserAlert> listOpenAlerts(UUID trainingActivityId) {
        requireProfessorCanManage(trainingActivityId);
        return alertRepository.findByTrainingActivity_IdAndStatusOrderByUpdatedAtDesc(
                trainingActivityId, SafeBrowserAlertStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public List<SafeBrowserEvent> listEvents(UUID trainingActivityId) {
        requireProfessorCanManage(trainingActivityId);
        return eventRepository.findByAssignment_TrainingActivity_IdOrderByOccurredAtDesc(trainingActivityId);
    }

    @Transactional(noRollbackFor = SafeBrowserSessionStartRejectedException.class)
    public SessionStart beginSession(UUID assignmentId) {
        var sample = Timer.start();
        try {
            var assignment = requireCurrentStudentAssignment(assignmentId);
            var now = Instant.now();
            ensureCanEstablishSession(assignment, now);
            sessionRepository.findFirstByAssignment_IdAndStatusInOrderByCreatedAtDesc(
                            assignmentId, List.of(SafeBrowserSessionStatus.PENDING, SafeBrowserSessionStatus.ACTIVE))
                    .ifPresent(session -> {
                        throw rejectedStart("This assignment already has an active Safe Browser session.");
                    });

            var token = newToken();
            var session = new SafeBrowserSession();
            session.setId(UUID.randomUUID());
            session.setAssignment(assignment);
            session.setTokenHash(hash(token));
            session.setStatus(SafeBrowserSessionStatus.PENDING);
            session.setStartedAt(now);
            session.setCreatedAt(now);
            session.setUpdatedAt(now);
            sessionRepository.save(session);
            sessionStartCounter.increment();
            LOGGER.info("event=safe_browser_session_started assignment_id={} session_id={}", assignmentId, session.getId());
            return new SessionStart(session.getId(), token);
        }
        finally {
            sample.stop(sessionStartLatency);
        }
    }

    @Transactional
    public TrainingActivityAssignment recordHeartbeat(UUID assignmentId, String token) {
        var sample = Timer.start();
        try {
            var assignment = requireCurrentStudentAssignment(assignmentId);
            var session = requireCurrentSession(assignmentId, token);
            var now = Instant.now();
            if (session.getStatus().isTerminal()) {
                throw new IllegalStateException("Safe Browser Mode session is no longer active.");
            }
            if (!isAnswerable(assignment)) {
                endSession(session, now);
                return deactivateSafeBrowserSession(assignment, now);
            }
            if (session.getStatus() == SafeBrowserSessionStatus.PENDING) {
                session.setStatus(SafeBrowserSessionStatus.ACTIVE);
                recordEvent(session, assignment, SafeBrowserEventType.SESSION_STARTED, SafeBrowserEventSeverity.INFO, now, UUID.randomUUID());
            }
            session.setLastHeartbeatAt(now);
            session.setUpdatedAt(now);
            sessionRepository.save(session);
            assignment.setSafeBrowserSessionActive(true);
            assignment.setSafeBrowserLastHeartbeatAt(now);
            assignment.setUpdatedAt(now);
            heartbeatCounter.increment();
            LOGGER.debug("event=safe_browser_heartbeat assignment_id={} session_id={}", assignmentId, session.getId());
            return assignmentRepository.save(assignment);
        }
        finally {
            sample.stop(heartbeatLatency);
        }
    }

    @Transactional
    public TrainingActivityAssignment reportViolation(UUID assignmentId, String token, SafeBrowserEventType eventType, UUID clientEventId) {
        var assignment = requireCurrentStudentAssignment(assignmentId);
        if (!isViolation(eventType)) {
            throw new IllegalArgumentException("Unsupported Safe Browser violation type.");
        }
        if (clientEventId == null) {
            throw new IllegalArgumentException("A Safe Browser violation requires a client event id.");
        }
        var session = requireCurrentSession(assignmentId, token);
        if (eventRepository.findBySession_IdAndClientEventId(session.getId(), clientEventId).isPresent()) {
            duplicateConflictCounter.increment();
            LOGGER.info("event=safe_browser_violation_duplicate assignment_id={} session_id={}", assignmentId, session.getId());
            return assignment;
        }
        if (session.getStatus() != SafeBrowserSessionStatus.ACTIVE || assignment.isSafeBrowserLocked()) {
            throw new IllegalStateException("Safe Browser Mode session is no longer active.");
        }
        return lockAssignment(assignment, session, eventType, clientEventId, SafeBrowserSessionStatus.VIOLATED, true);
    }

    @Transactional
    public TrainingActivityAssignment deactivateSession(UUID assignmentId, String token) {
        var assignment = requireCurrentStudentAssignment(assignmentId);
        var session = requireCurrentSession(assignmentId, token);
        endSession(session, Instant.now());
        return deactivateSafeBrowserSession(assignment, Instant.now());
    }

    @Transactional
    public TrainingActivityAssignment unlockAssignment(UUID assignmentId) {
        var assignment = requireAssignment(assignmentId);
        requireProfessorCanManage(assignment.getTrainingActivity().getId());
        if (!assignment.isSafeBrowserLocked()) {
            return assignment;
        }
        var now = Instant.now();
        var latestSession = sessionRepository.findFirstByAssignment_IdAndStatusInOrderByCreatedAtDesc(
                assignmentId, List.of(SafeBrowserSessionStatus.PENDING, SafeBrowserSessionStatus.ACTIVE,
                        SafeBrowserSessionStatus.VIOLATED, SafeBrowserSessionStatus.EXPIRED));
        latestSession.ifPresent(session -> {
            if (!session.getStatus().isTerminal()) {
                endSession(session, now);
            }
            recordEvent(session, assignment, SafeBrowserEventType.MANUAL_UNLOCK, SafeBrowserEventSeverity.INFO, now, UUID.randomUUID());
        });
        assignment.setSafeBrowserLocked(false);
        assignment.setSafeBrowserSessionActive(false);
        assignment.setSafeBrowserLockReason(null);
        assignment.setUpdatedAt(now);
        var saved = assignmentRepository.save(assignment);
        publishAfterCommit(saved, false);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireStaleSession(
            UUID assignmentId, UUID sessionId, SafeBrowserSessionStatus expectedStatus, Instant cutoff, Instant now) {
        var assignment = requireLockedAssignment(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown training assignment %s".formatted(assignmentId)));
        var session = sessionRepository.findById(sessionId)
                .filter(candidate -> candidate.getAssignment().getId().equals(assignmentId))
                .filter(candidate -> candidate.getStatus() == expectedStatus)
                .filter(candidate -> isStale(candidate, expectedStatus, cutoff))
                .orElse(null);
        if (session == null) {
            return false;
        }
        if (expectedStatus == SafeBrowserSessionStatus.PENDING) {
            endSessionAsExpired(session, now);
            return true;
        }
        if (!isAnswerable(assignment)) {
            endSession(session, now);
            deactivateSafeBrowserSession(assignment, now);
            return true;
        }
        lockAssignment(assignment, session, SafeBrowserEventType.HEARTBEAT_LOST, UUID.randomUUID(), SafeBrowserSessionStatus.EXPIRED, true);
        return true;
    }

    private TrainingActivityAssignment lockAssignment(
            TrainingActivityAssignment assignment,
            SafeBrowserSession session,
            SafeBrowserEventType eventType,
            UUID clientEventId,
            SafeBrowserSessionStatus terminalStatus,
            boolean createAlert) {
        var now = Instant.now();
        if (!isAnswerable(assignment)) {
            endSession(session, now);
            recordEvent(session, assignment, eventType, SafeBrowserEventSeverity.VIOLATION, now, clientEventId);
            return deactivateSafeBrowserSession(assignment, now);
        }
        recordEvent(session, assignment, eventType, SafeBrowserEventSeverity.VIOLATION, now, clientEventId);
        session.setStatus(terminalStatus);
        session.setEndedAt(now);
        session.setUpdatedAt(now);
        sessionRepository.save(session);
        assignment.setSafeBrowserLocked(true);
        assignment.setSafeBrowserLockedAt(now);
        assignment.setSafeBrowserLockReason(eventType.name());
        assignment.setSafeBrowserSessionActive(false);
        assignment.setUpdatedAt(now);
        var saved = assignmentRepository.save(assignment);
        if (createAlert) {
            upsertAlert(saved, now);
        }
        publishAfterCommit(saved, true);
        return saved;
    }

    private TrainingActivityAssignment deactivateSafeBrowserSession(TrainingActivityAssignment assignment, Instant now) {
        if (!assignment.isSafeBrowserSessionActive()) {
            return assignment;
        }
        assignment.setSafeBrowserSessionActive(false);
        assignment.setUpdatedAt(now);
        var saved = assignmentRepository.save(assignment);
        publishAfterCommit(saved, saved.isSafeBrowserLocked());
        return saved;
    }

    private void endSession(SafeBrowserSession session, Instant now) {
        if (session.getStatus().isTerminal()) {
            return;
        }
        session.setStatus(SafeBrowserSessionStatus.ENDED);
        session.setEndedAt(now);
        session.setUpdatedAt(now);
        sessionRepository.save(session);
    }

    private void endSessionAsExpired(SafeBrowserSession session, Instant now) {
        session.setStatus(SafeBrowserSessionStatus.EXPIRED);
        session.setEndedAt(now);
        session.setUpdatedAt(now);
        sessionRepository.save(session);
    }

    private SafeBrowserSession requireCurrentSession(UUID assignmentId, String token) {
        if (token == null || token.isBlank()) {
            throw new SecurityException("Safe Browser session token is required.");
        }
        var session = sessionRepository.findFirstByAssignment_IdAndStatusInOrderByCreatedAtDesc(
                        assignmentId, List.of(SafeBrowserSessionStatus.PENDING, SafeBrowserSessionStatus.ACTIVE,
                                SafeBrowserSessionStatus.VIOLATED, SafeBrowserSessionStatus.EXPIRED, SafeBrowserSessionStatus.ENDED))
                .orElseThrow(() -> new IllegalStateException("No Safe Browser session is available for this assignment."));
        if (!MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.UTF_8), session.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid Safe Browser session token.");
        }
        return session;
    }

    private void publishAfterCommit(TrainingActivityAssignment assignment, boolean locked) {
        var notification = new SafeBrowserAssignmentStateBus.Notification(
                assignment.getTrainingActivity().getId(), assignment.getId(), assignment.getGroupClassMember().getId(), locked,
                assignment.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            assignmentStateBus.publish(notification);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                assignmentStateBus.publish(notification);
            }
        });
    }

    private void upsertAlert(TrainingActivityAssignment assignment, Instant eventTime) {
        var activity = assignment.getTrainingActivity();
        var alert = alertRepository.findByProfessorTenantAccount_IdAndTrainingActivity_IdAndStatus(
                        activity.getCreatedByTenantAccount().getId(), activity.getId(), SafeBrowserAlertStatus.OPEN)
                .orElseGet(() -> newAlert(assignment, eventTime));
        alert.setIncidentCount(alert.getIncidentCount() + 1);
        alert.setLastEventAt(eventTime);
        alert.setUpdatedAt(eventTime);
        alertRepository.save(alert);
    }

    private SafeBrowserAlert newAlert(TrainingActivityAssignment assignment, Instant eventTime) {
        var activity = assignment.getTrainingActivity();
        var alert = new SafeBrowserAlert();
        alert.setId(UUID.randomUUID());
        alert.setTrainingActivity(activity);
        alert.setProfessorTenantAccount(activity.getCreatedByTenantAccount());
        alert.setProfessorGroupClassMember(activity.getCreatedByGroupClassMember());
        alert.setStatus(SafeBrowserAlertStatus.OPEN);
        alert.setIncidentCount(0);
        alert.setLastEventAt(eventTime);
        alert.setCreatedAt(eventTime);
        alert.setUpdatedAt(eventTime);
        return alert;
    }

    private void ensureCanEstablishSession(TrainingActivityAssignment assignment, Instant now) {
        if (!assignment.getTrainingActivity().isSafeBrowserEnabled()) {
            throw rejectedStart("Safe Browser Mode is not enabled for this assignment.");
        }
        if (assignment.isSafeBrowserLocked()) {
            throw rejectedStart("Safe Browser Mode was interrupted. Ask your professor to review this assignment.");
        }
        if (!isAnswerable(assignment)) {
            throw rejectedStart("The evaluation assignment is no longer answerable.");
        }
    }

    private boolean isAnswerable(TrainingActivityAssignment assignment) {
        var activity = assignment.getTrainingActivity();
        var now = Instant.now();
        return assignment.getStatus() != TrainingActivityAssignmentStatus.SUBMITTED
                && !assignment.getStatus().isTerminal()
                && activity.getStatus() == TrainingActivityLifecycleStatus.PUBLISHED
                && (activity.getOpensAt() == null || !now.isBefore(activity.getOpensAt()))
                && (activity.getClosesAt() == null || now.isBefore(activity.getClosesAt()))
                && !assignment.isSafeBrowserLocked();
    }

    private TrainingActivityAssignment requireCurrentStudentAssignment(UUID assignmentId) {
        var context = contextResolver.requireCurrent();
        if (context.groupClassKind() != GroupClassMemberKind.STUDENT) {
            throw new SecurityException("Only students can use Safe Browser Mode for assigned evaluations.");
        }
        return requireLockedAssignment(assignmentId)
                .filter(assignment -> context.groupClassMemberId().equals(assignment.getGroupClassMember().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown training assignment %s".formatted(assignmentId)));
    }

    private TrainingActivityAssignment requireAssignment(UUID assignmentId) {
        return requireLockedAssignment(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown training assignment %s".formatted(assignmentId)));
    }

    private java.util.Optional<TrainingActivityAssignment> requireLockedAssignment(UUID assignmentId) {
        return assignmentRepository.findLockedWithTrainingActivityById(assignmentId);
    }

    private static boolean isStale(SafeBrowserSession session, SafeBrowserSessionStatus expectedStatus, Instant cutoff) {
        var observedAt = expectedStatus == SafeBrowserSessionStatus.PENDING ? session.getCreatedAt() : session.getLastHeartbeatAt();
        return observedAt != null && observedAt.isBefore(cutoff);
    }

    private ActiveAcademicContext requireProfessorCanManage(UUID trainingActivityId) {
        var context = contextResolver.requireCurrent();
        if (context.groupClassKind() != GroupClassMemberKind.PROFESSOR) {
            throw new SecurityException("Only professors can manage Safe Browser incidents.");
        }
        var activity = activityRepository.findById(trainingActivityId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown training activity %s".formatted(trainingActivityId)));
        if (!context.groupClassId().equals(activity.getGroupClass().getId())) {
            throw new SecurityException("The current class context cannot manage this activity.");
        }
        return context;
    }

    private void recordEvent(
            SafeBrowserSession session,
            TrainingActivityAssignment assignment,
            SafeBrowserEventType eventType,
            SafeBrowserEventSeverity severity,
            Instant occurredAt,
            UUID clientEventId) {
        var event = new SafeBrowserEvent();
        event.setId(UUID.randomUUID());
        event.setAssignment(assignment);
        event.setSession(session);
        contextResolver.resolveCurrent().ifPresent(context -> {
            var actor = new GroupClassMember();
            actor.setId(context.groupClassMemberId());
            event.setActorGroupClassMember(actor);
        });
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setOccurredAt(occurredAt);
        event.setClientEventId(clientEventId);
        event.setCreatedAt(Instant.now());
        eventRepository.save(event);
    }

    private static boolean isViolation(SafeBrowserEventType eventType) {
        return eventType == SafeBrowserEventType.FULLSCREEN_EXIT
                || eventType == SafeBrowserEventType.TAB_HIDDEN
                || eventType == SafeBrowserEventType.WINDOW_BLUR
                || eventType == SafeBrowserEventType.BEFORE_UNLOAD
                || eventType == SafeBrowserEventType.HEARTBEAT_LOST;
    }

    private static String newToken() {
        var bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private SafeBrowserSessionStartRejectedException rejectedStart(String message) {
        return new SafeBrowserSessionStartRejectedException(message);
    }

    public record SessionStart(UUID sessionId, String token) {}

    private static final class SafeBrowserSessionStartRejectedException extends IllegalStateException {

        private SafeBrowserSessionStartRejectedException(String message) {
            super(message);
        }
    }
}
