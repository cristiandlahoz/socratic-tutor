package com.wornux.services.training_activity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.TrainingActivityAiJob;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobType;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.entities.training_activity.TrainingActivityTurn;
import com.wornux.data.repositories.training_activity.TrainingActivityAiJobRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityTurnRepository;
import com.wornux.services.context.ActiveAcademicContextResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Accepts student commands only. Model invocation belongs to {@link TrainingTutorJobService};
 * no Vaadin request can retain a transaction while the configured model runs.
 */
@Service
public class TrainingAssignmentEvaluationService {
    private static final int TUTOR_PRIORITY = 10;
    private static final int MAX_ATTEMPTS = 3;

    private final TrainingActivityAssignmentRepository assignmentRepository;
    private final TrainingActivityTurnRepository turnRepository;
    private final TrainingActivityAiJobRepository jobRepository;
    private final ActiveAcademicContextResolver contextResolver;
    private final TrainingTutorJobService tutorJobService;

    @Autowired
    public TrainingAssignmentEvaluationService(
            TrainingActivityAssignmentRepository assignmentRepository,
            TrainingActivityTurnRepository turnRepository,
            TrainingActivityAiJobRepository jobRepository,
            ActiveAcademicContextResolver contextResolver,
            TrainingTutorJobService tutorJobService) {
        this.assignmentRepository = assignmentRepository;
        this.turnRepository = turnRepository;
        this.jobRepository = jobRepository;
        this.contextResolver = contextResolver;
        this.tutorJobService = tutorJobService;
    }

    @Transactional(readOnly = true)
    public TrainingActivityAssignmentSnapshot getForCurrentStudent(UUID assignmentId) {
        return snapshot(requireOwned(assignmentId));
    }

    @Transactional
    public TrainingActivityAssignmentSnapshot start(UUID assignmentId) {
        var assignment = requireOwnedLocked(assignmentId);
        return startLocked(assignment);
    }

    /** Starts an already-authorized student's assignment from a background UI task. */
    @Transactional
    public TrainingActivityAssignmentSnapshot startForStudent(UUID assignmentId, UUID studentMemberId) {
        var assignment = assignmentRepository.findLockedWithTrainingActivityById(assignmentId)
                .filter(value -> studentMemberId.equals(value.getGroupClassMember().getId()))
                .orElseThrow(() -> new SecurityException("Unknown training assignment %s".formatted(assignmentId)));
        return startLocked(assignment);
    }

    private TrainingActivityAssignmentSnapshot startLocked(TrainingActivityAssignment assignment) {
        ensureAnswerable(assignment);
        if (assignment.getStatus() != TrainingActivityAssignmentStatus.ASSIGNED) {
            return snapshot(assignment);
        }
        var now = Instant.now();
        assignment.setStatus(TrainingActivityAssignmentStatus.STARTING);
        assignment.setStartedAt(now);
        assignment.setUpdatedAt(now);
        assignmentRepository.saveAndFlush(assignment);
        enqueueIfAbsent(TrainingActivityAiJobType.FIRST_QUESTION, assignment, null, assignment.getVersion(),
                "first:" + assignment.getId(), now);
        return snapshot(assignment);
    }

    @Transactional
    public TrainingActivityAssignmentSnapshot submitAnswer(UUID assignmentId, String answer, UUID answerSubmissionId) {
        if (answerSubmissionId == null) {
            throw new IllegalArgumentException("A response submission id is required.");
        }
        validateRequiredAnswer(answer);
        var assignment = requireOwnedLocked(assignmentId);
        ensureAnswerable(assignment);
        var duplicate = turnRepository.findByAssignment_IdAndAnswerSubmissionId(assignmentId, answerSubmissionId);
        if (duplicate.isPresent()) {
            if (!Objects.equals(duplicate.get().getAnswerText(), answer)) {
                throw new IllegalArgumentException("The response submission id was already used for a different answer.");
            }
            return snapshot(assignment);
        }
        if (assignment.getStatus() != TrainingActivityAssignmentStatus.WAITING_FOR_ANSWER) {
            throw new IllegalStateException("The evaluation is not waiting for an answer.");
        }
        var turn = turnRepository.findFirstByAssignment_IdAndAnswerTextIsNullOrderBySequenceNumberDesc(assignmentId)
                .orElseThrow(() -> new IllegalStateException("The current tutor question is unavailable."));
        var now = Instant.now();
        turn.setAnswerText(answer);
        turn.setAnswerSubmissionId(answerSubmissionId);
        turn.setAnswerSubmittedAt(now);
        turn.setUpdatedAt(now);
        turnRepository.save(turn);
        assignment.setStatus(TrainingActivityAssignmentStatus.WAITING_FOR_TUTOR);
        assignment.setUpdatedAt(now);
        assignmentRepository.saveAndFlush(assignment);
        enqueueIfAbsent(TrainingActivityAiJobType.NEXT_DECISION, assignment, turn, assignment.getVersion(),
                "next:" + assignmentId + ":" + turn.getId(), now);
        return snapshot(assignment);
    }

    @Transactional
    public TrainingActivityAssignmentSnapshot retryTutor(UUID assignmentId) {
        requireOwnedLocked(assignmentId);
        if (tutorJobService == null || !tutorJobService.retryTemporaryFailure(assignmentId)) {
            throw new IllegalStateException("The tutor is not awaiting recovery for this evaluation.");
        }
        return snapshot(requireOwned(assignmentId));
    }

    private TrainingActivityAssignment requireOwned(UUID assignmentId) {
        var context = contextResolver.requireCurrent();
        if (context.groupClassKind() != GroupClassMemberKind.STUDENT) {
            throw new SecurityException("Only students can answer assigned evaluations.");
        }
        return assignmentRepository.findWithTrainingActivityById(assignmentId)
                .filter(assignment -> context.groupClassMemberId().equals(assignment.getGroupClassMember().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown training assignment %s".formatted(assignmentId)));
    }

    private TrainingActivityAssignment requireOwnedLocked(UUID assignmentId) {
        var context = contextResolver.requireCurrent();
        if (context.groupClassKind() != GroupClassMemberKind.STUDENT) {
            throw new SecurityException("Only students can answer assigned evaluations.");
        }
        return assignmentRepository.findLockedWithTrainingActivityById(assignmentId)
                .filter(assignment -> context.groupClassMemberId().equals(assignment.getGroupClassMember().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown training assignment %s".formatted(assignmentId)));
    }

    private void ensureAnswerable(TrainingActivityAssignment assignment) {
        var activity = assignment.getTrainingActivity();
        var now = Instant.now();
        if (assignment.getStatus().isTerminal()
                || activity.getStatus() != TrainingActivityLifecycleStatus.PUBLISHED
                || (activity.getOpensAt() != null && now.isBefore(activity.getOpensAt()))
                || (activity.getClosesAt() != null && !now.isBefore(activity.getClosesAt()))) {
            throw new IllegalStateException("The evaluation assignment is no longer answerable.");
        }
        if (assignment.isSafeBrowserLocked()) {
            throw new IllegalStateException("Safe Browser Mode was interrupted. Ask your professor to review this assignment.");
        }
        if (assignment.getTrainingActivity().isSafeBrowserEnabled() && !assignment.isSafeBrowserSessionActive()) {
            throw new IllegalStateException("Safe Browser Mode must be active before answering.");
        }
    }

    private void validateRequiredAnswer(String answer) {
        if (answer == null || answer.trim().isBlank()) {
            throw new IllegalArgumentException("Escribe una respuesta antes de continuar");
        }
    }

    private void enqueueIfAbsent(
            TrainingActivityAiJobType type, TrainingActivityAssignment assignment, TrainingActivityTurn turn,
            long inputVersion, String semanticKey, Instant now) {
        jobRepository.insertTutorJobIfAbsent(UUID.randomUUID(), type.name(), TUTOR_PRIORITY,
                assignment.getTrainingActivity().getId(), assignment.getId(), turn == null ? null : turn.getId(), null,
                inputVersion, semanticKey, MAX_ATTEMPTS, now, now, now);
    }

    public TrainingActivityAssignmentSnapshot snapshot(TrainingActivityAssignment assignment) {
        var turns = turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(assignment.getId());
        var transcript = turns.stream().filter(turn -> turn.getAnswerText() != null)
                .map(turn -> new EvaluationExchange(turn.getQuestionText(), turn.getAnswerText())).toList();
        var currentQuestion = turns.stream().filter(turn -> turn.getAnswerText() == null)
                .map(TrainingActivityTurn::getQuestionText).reduce((first, second) -> second).orElse(null);
        return new TrainingActivityAssignmentSnapshot(assignment, transcript, currentQuestion, turns.size());
    }

    public record EvaluationExchange(String question, String answer) {}
}
