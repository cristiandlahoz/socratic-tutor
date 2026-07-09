package com.wornux.services.training_activity;

import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TrainingAssignmentDecisionPersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrainingAssignmentDecisionPersistenceService.class);

    private final TrainingActivityAssignmentRepository assignmentRepository;
    private final TrainingActivityRepository activityRepository;
    private final TrainingAssignmentTutorService tutorService;
    private final SafeBrowserAssignmentStateBus assignmentStateBus;
    private final JsonMapper jsonMapper;

    public TrainingAssignmentDecisionPersistenceService(
            TrainingActivityAssignmentRepository assignmentRepository,
            TrainingActivityRepository activityRepository,
            TrainingAssignmentTutorService tutorService,
            SafeBrowserAssignmentStateBus assignmentStateBus,
            JsonMapper jsonMapper) {
        this.assignmentRepository = assignmentRepository;
        this.activityRepository = activityRepository;
        this.tutorService = tutorService;
        this.assignmentStateBus = assignmentStateBus;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public TrainingActivityAssignment applyDecision(
            UUID assignmentId,
            UUID studentGroupClassMemberId,
            List<TrainingAssignmentEvaluationService.EvaluationExchange> transcript,
            AdaptiveTutorDecision decision,
            String finalReport) {
        var assignment = getForStudent(assignmentId, studentGroupClassMemberId);
        if (isStaleDecisionBlocked(assignment)) {
            return initializeUiSafeRelations(assignment);
        }
        assignment.setEvaluationTranscript(writeTranscript(transcript));
        applyDecisionMetadata(assignment, decision);
        if (decision.type() == TutorDecisionType.COMPLETE_SUCCESS
                || decision.type() == TutorDecisionType.COMPLETE_INSUFFICIENT_EVIDENCE) {
            assignment.setStatus(TrainingActivityAssignmentStatus.SUBMITTED);
            assignment.setSubmittedAt(Instant.now());
            assignment.setCurrentQuestion(null);
            assignment.setSafeBrowserSessionActive(false);
            assignment.setInsufficientEvidence(decision.type() == TutorDecisionType.COMPLETE_INSUFFICIENT_EVIDENCE);
            assignment.setFinalReport(finalReport);
        }
        else {
            assignment.setStatus(TrainingActivityAssignmentStatus.STARTED);
            assignment.setCurrentQuestion(decision.questionText());
            assignment.setQuestionCount(assignment.getQuestionCount() + 1);
        }
        assignment.setUpdatedAt(Instant.now());
        var saved = assignmentRepository.save(assignment);
        var activityClosed = closeActivityIfAllAssignmentsAreTerminal(saved);
        if (activityClosed) {
            publishActivityStateAfterCommit(saved.getTrainingActivity().getId());
        }
        else if (saved.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            publishAfterCommit(new SafeBrowserAssignmentStateBus.Notification(
                    saved.getTrainingActivity().getId(),
                    saved.getId(),
                    saved.getGroupClassMember().getId(),
                    saved.isSafeBrowserLocked(),
                    saved.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED));
        }
        return loadCanonicalAssignmentOrFallback(saved, studentGroupClassMemberId);
    }

    private TrainingActivityAssignment loadCanonicalAssignmentOrFallback(
            TrainingActivityAssignment saved,
            UUID studentGroupClassMemberId) {
        try {
            return assignmentRepository.findWithTrainingActivityById(saved.getId())
                    .filter(assignment -> studentGroupClassMemberId.equals(assignment.getGroupClassMember().getId()))
                    .map(this::initializeUiSafeRelations)
                    .orElseGet(() -> initializeUiSafeRelations(saved));
        }
        catch (RuntimeException exception) {
            LOGGER.warn(
                    "Training assignment canonical reload failed after decision persistence: assignmentId={} activityId={} studentGroupClassMemberId={}",
                    saved.getId(),
                    saved.getTrainingActivity().getId(),
                    studentGroupClassMemberId,
                    exception);
            return initializeUiSafeRelations(saved);
        }
    }

    private TrainingActivityAssignment initializeUiSafeRelations(TrainingActivityAssignment assignment) {
        var activity = assignment.getTrainingActivity();
        activity.getId();
        activity.getStatus();
        activity.isSafeBrowserEnabled();
        var member = assignment.getGroupClassMember();
        member.getId();
        if (member.getGroupClass() != null) {
            member.getGroupClass().getId();
        }
        return assignment;
    }

    private TrainingActivityAssignment getForStudent(UUID assignmentId, UUID studentGroupClassMemberId) {
        return assignmentRepository.findWithTrainingActivityById(assignmentId)
                .filter(assignment -> studentGroupClassMemberId.equals(assignment.getGroupClassMember().getId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown training assignment %s".formatted(assignmentId)));
    }

    private boolean isStaleDecisionBlocked(TrainingActivityAssignment assignment) {
        return assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED
                || assignment.getStatus().isTerminal()
                || assignment.isSafeBrowserLocked()
                || assignment.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED;
    }

    private boolean closeActivityIfAllAssignmentsAreTerminal(TrainingActivityAssignment assignment) {
        if (!assignment.getStatus().isTerminal()) {
            return false;
        }
        var activity = assignment.getTrainingActivity();
        if (activity.getStatus() != TrainingActivityLifecycleStatus.PUBLISHED) {
            return false;
        }
        var assignments = assignmentRepository.findByTrainingActivity_IdOrderByUpdatedAtDesc(activity.getId());
        if (assignments.stream().allMatch(candidate -> candidate.getStatus().isTerminal())) {
            var now = Instant.now();
            activity.setStatus(TrainingActivityLifecycleStatus.CLOSED);
            activity.setClosesAt(now);
            activity.setUpdatedAt(now);
            activityRepository.save(activity);
            return true;
        }
        return false;
    }

    private void publishAfterCommit(SafeBrowserAssignmentStateBus.Notification notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safelyPublish(notification);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safelyPublish(notification);
            }
        });
    }

    private void publishActivityStateAfterCommit(UUID trainingActivityId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safelyPublishActivityState(trainingActivityId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safelyPublishActivityState(trainingActivityId);
            }
        });
    }

    private void safelyPublishActivityState(UUID trainingActivityId) {
        try {
            var assignments = assignmentRepository.findByTrainingActivity_IdOrderByUpdatedAtDesc(trainingActivityId);
            assignments.stream()
                    .map(assignment -> new SafeBrowserAssignmentStateBus.Notification(
                            assignment.getTrainingActivity().getId(),
                            assignment.getId(),
                            assignment.getGroupClassMember().getId(),
                            assignment.isSafeBrowserLocked(),
                            assignment.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED))
                    .forEach(this::safelyPublish);
        }
        catch (RuntimeException exception) {
            LOGGER.warn(
                    "Training assignment activity-state publication failed after commit: trainingActivityId={}",
                    trainingActivityId,
                    exception);
        }
    }

    private void safelyPublish(SafeBrowserAssignmentStateBus.Notification notification) {
        try {
            assignmentStateBus.publish(notification);
        }
        catch (RuntimeException exception) {
            LOGGER.warn(
                    "Training assignment state publication failed after commit: assignmentId={} trainingActivityId={} studentGroupClassMemberId={}",
                    notification.assignmentId(),
                    notification.trainingActivityId(),
                    notification.groupClassMemberId(),
                    exception);
        }
    }

    public void applyDecisionMetadata(TrainingActivityAssignment assignment, AdaptiveTutorDecision decision) {
        assignment.setLastTutorDecisionJson(writeDecision(decision));
        assignment.setTutorAnswerQuality(decision.answerQuality());
        assignment.setTutorEvidenceStatus(decision.evidenceStatus());
        assignment.setTutorCoverageStatus(decision.coverageStatus());
        assignment.setTutorPedagogicalMove(decision.pedagogicalMove());
        assignment.setCoveredInstructionAspectsJson(writeStrings(decision.coveredInstructionAspects()));
        assignment.setMissingInstructionAspectsJson(writeStrings(decision.missingInstructionAspects()));
        assignment.setUnproductivePatternDetected(decision.unproductivePatternDetected());
        assignment.setTutorDecisionReason(decision.reason());
        assignment.setTutorModelName(tutorService.currentModelName());
        assignment.setTutorPromptVersion(tutorService.promptVersion());
    }

    private String writeTranscript(List<TrainingAssignmentEvaluationService.EvaluationExchange> transcript) {
        try {
            return jsonMapper.writeValueAsString(transcript);
        }
        catch (JacksonException exception) {
            throw new IllegalStateException("Could not write training assignment transcript.", exception);
        }
    }

    private String writeDecision(AdaptiveTutorDecision decision) {
        try {
            return jsonMapper.writeValueAsString(decision);
        }
        catch (JacksonException exception) {
            throw new IllegalStateException("Could not write adaptive tutor decision.", exception);
        }
    }

    private String writeStrings(List<String> values) {
        try {
            return jsonMapper.writeValueAsString(values == null ? List.of() : values);
        }
        catch (JacksonException exception) {
            throw new IllegalStateException("Could not write adaptive tutor aspects.", exception);
        }
    }
}
