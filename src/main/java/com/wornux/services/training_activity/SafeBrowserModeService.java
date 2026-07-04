package com.wornux.services.training_activity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.SafeBrowserAlert;
import com.wornux.data.entities.training_activity.SafeBrowserAlertStatus;
import com.wornux.data.entities.training_activity.SafeBrowserEvent;
import com.wornux.data.entities.training_activity.SafeBrowserEventSeverity;
import com.wornux.data.entities.training_activity.SafeBrowserEventType;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.training_activity.SafeBrowserAlertRepository;
import com.wornux.data.repositories.training_activity.SafeBrowserEventRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SafeBrowserModeService {

    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);

    private final TrainingActivityAssignmentRepository assignmentRepository;
    private final TrainingActivityRepository activityRepository;
    private final SafeBrowserEventRepository eventRepository;
    private final SafeBrowserAlertRepository alertRepository;
    private final ActiveAcademicContextResolver contextResolver;
    private final SafeBrowserAssignmentStateBus assignmentStateBus;

    public SafeBrowserModeService(
            TrainingActivityAssignmentRepository assignmentRepository,
            TrainingActivityRepository activityRepository,
            SafeBrowserEventRepository eventRepository,
            SafeBrowserAlertRepository alertRepository,
            ActiveAcademicContextResolver contextResolver,
            SafeBrowserAssignmentStateBus assignmentStateBus) {
        this.assignmentRepository = assignmentRepository;
        this.activityRepository = activityRepository;
        this.eventRepository = eventRepository;
        this.alertRepository = alertRepository;
        this.contextResolver = contextResolver;
        this.assignmentStateBus = assignmentStateBus;
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

    @Transactional
    public TrainingActivityAssignment startSession(UUID assignmentId) {
        var assignment = requireCurrentStudentAssignment(assignmentId);
        ensureCanEstablishSession(assignment);
        var now = Instant.now();
        assignment.setSafeBrowserSessionActive(true);
        assignment.setSafeBrowserLastHeartbeatAt(now);
        assignment.setUpdatedAt(now);
        recordEvent(assignment, SafeBrowserEventType.SESSION_STARTED, SafeBrowserEventSeverity.INFO, now);
        return assignmentRepository.save(assignment);
    }

    @Transactional
    public void recordHeartbeat(UUID assignmentId) {
        var assignment = requireCurrentStudentAssignment(assignmentId);
        if (!assignment.getTrainingActivity().isSafeBrowserEnabled() || assignment.isSafeBrowserLocked()) {
            return;
        }
        var now = Instant.now();
        assignment.setSafeBrowserSessionActive(true);
        assignment.setSafeBrowserLastHeartbeatAt(now);
        assignment.setUpdatedAt(now);
        assignmentRepository.save(assignment);
    }

    @Transactional
    public TrainingActivityAssignment reportViolation(UUID assignmentId, SafeBrowserEventType eventType) {
        var assignment = requireCurrentStudentAssignment(assignmentId);
        if (!isViolation(eventType)) {
            throw new IllegalArgumentException("Unsupported Safe Browser violation type.");
        }
        return lockAssignment(assignment, eventType, true);
    }

    @Transactional
    public TrainingActivityAssignment unlockAssignment(UUID assignmentId) {
        var assignment = requireAssignment(assignmentId);
        requireProfessorCanManage(assignment.getTrainingActivity().getId());
        if (!assignment.isSafeBrowserLocked()) {
            return assignment;
        }
        var now = Instant.now();
        assignment.setSafeBrowserLocked(false);
        assignment.setSafeBrowserSessionActive(false);
        assignment.setSafeBrowserLockReason(null);
        assignment.setUpdatedAt(now);
        recordEvent(assignment, SafeBrowserEventType.MANUAL_UNLOCK, SafeBrowserEventSeverity.INFO, now);
        var saved = assignmentRepository.save(assignment);
        publishAfterCommit(saved, false);
        return saved;
    }

    @Transactional
    public int lockExpiredSessions() {
        var cutoff = Instant.now().minus(HEARTBEAT_TIMEOUT);
        var expired = assignmentRepository.findBySafeBrowserSessionActiveTrue()
                .stream()
                .filter(assignment -> assignment.getTrainingActivity().isSafeBrowserEnabled())
                .filter(assignment -> !assignment.isSafeBrowserLocked())
                .filter(assignment -> assignment.getSafeBrowserLastHeartbeatAt() != null)
                .filter(assignment -> assignment.getSafeBrowserLastHeartbeatAt().isBefore(cutoff))
                .toList();
        expired.forEach(assignment -> lockAssignment(assignment, SafeBrowserEventType.HEARTBEAT_LOST, true));
        return expired.size();
    }

    private TrainingActivityAssignment lockAssignment(
            TrainingActivityAssignment assignment,
            SafeBrowserEventType eventType,
            boolean createAlert) {
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            recordEvent(assignment, eventType, SafeBrowserEventSeverity.VIOLATION, Instant.now());
            return assignment;
        }
        if (assignment.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED) {
            recordEvent(assignment, eventType, SafeBrowserEventSeverity.VIOLATION, Instant.now());
            return assignment;
        }
        var now = Instant.now();
        recordEvent(assignment, eventType, SafeBrowserEventSeverity.VIOLATION, now);
        if (!assignment.isSafeBrowserLocked()) {
            assignment.setSafeBrowserLocked(true);
            assignment.setSafeBrowserLockedAt(now);
            assignment.setSafeBrowserLockReason(eventType.name());
        }
        assignment.setSafeBrowserSessionActive(false);
        assignment.setUpdatedAt(now);
        var saved = assignmentRepository.save(assignment);
        if (createAlert) {
            upsertAlert(saved, now);
        }
        publishAfterCommit(saved, true);
        return saved;
    }

    private void publishAfterCommit(TrainingActivityAssignment assignment, boolean locked) {
        var notification = new SafeBrowserAssignmentStateBus.Notification(
                assignment.getId(),
                assignment.getGroupClassMember().getId(),
                locked);
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
        var professorTenantAccount = activity.getCreatedByTenantAccount();
        var alert = alertRepository.findByProfessorTenantAccount_IdAndTrainingActivity_IdAndStatus(
                professorTenantAccount.getId(), activity.getId(), SafeBrowserAlertStatus.OPEN)
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

    private void ensureCanEstablishSession(TrainingActivityAssignment assignment) {
        if (!assignment.getTrainingActivity().isSafeBrowserEnabled()) {
            return;
        }
        if (assignment.isSafeBrowserLocked()) {
            throw new IllegalStateException("Safe Browser Mode was interrupted. Ask your professor to review this assignment.");
        }
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            throw new IllegalStateException("Submitted assignments cannot be reopened.");
        }
        if (assignment.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED) {
            throw new IllegalStateException("The evaluation window has ended.");
        }
        if (assignment.isSafeBrowserSessionActive()) {
            throw new IllegalStateException("This assignment already has an active Safe Browser session.");
        }
    }

    private TrainingActivityAssignment requireCurrentStudentAssignment(UUID assignmentId) {
        var context = contextResolver.requireCurrent();
        if (context.groupClassKind() != GroupClassMemberKind.STUDENT) {
            throw new SecurityException("Only students can use Safe Browser Mode for assigned evaluations.");
        }
        return assignmentRepository.findWithTrainingActivityById(assignmentId)
                .filter(assignment -> context.groupClassMemberId().equals(assignment.getGroupClassMember().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown training assignment %s".formatted(assignmentId)));
    }

    private TrainingActivityAssignment requireAssignment(UUID assignmentId) {
        return assignmentRepository.findWithTrainingActivityById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown training assignment %s".formatted(assignmentId)));
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
            TrainingActivityAssignment assignment,
            SafeBrowserEventType eventType,
            SafeBrowserEventSeverity severity,
            Instant occurredAt) {
        var event = new SafeBrowserEvent();
        event.setId(UUID.randomUUID());
        event.setAssignment(assignment);
        contextResolver.resolveCurrent().ifPresent(context -> {
            var actor = new GroupClassMember();
            actor.setId(context.groupClassMemberId());
            event.setActorGroupClassMember(actor);
        });
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setOccurredAt(occurredAt);
        event.setCreatedAt(Instant.now());
        eventRepository.save(event);
    }

    private boolean isViolation(SafeBrowserEventType eventType) {
        return eventType == SafeBrowserEventType.FULLSCREEN_EXIT
                || eventType == SafeBrowserEventType.TAB_HIDDEN
                || eventType == SafeBrowserEventType.WINDOW_BLUR
                || eventType == SafeBrowserEventType.BEFORE_UNLOAD
                || eventType == SafeBrowserEventType.HEARTBEAT_LOST;
    }
}
