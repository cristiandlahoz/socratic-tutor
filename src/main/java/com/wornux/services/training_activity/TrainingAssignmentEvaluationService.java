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
import reactor.core.publisher.Flux;

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

    /** Compatibility constructor for focused tests that do not exercise recovery. */
    public TrainingAssignmentEvaluationService(
            TrainingActivityAssignmentRepository assignmentRepository,
            TrainingActivityTurnRepository turnRepository,
            TrainingActivityAiJobRepository jobRepository,
            ActiveAcademicContextResolver contextResolver) {
        this(assignmentRepository, turnRepository, jobRepository, contextResolver, null);
    }

    @Transactional(readOnly = true)
    public TrainingActivityAssignment getForCurrentStudent(UUID assignmentId) {
        return hydrate(requireOwned(assignmentId));
    }

    @Transactional
    public TrainingActivityAssignment start(UUID assignmentId) {
        var assignment = requireOwnedLocked(assignmentId);
        ensureAnswerable(assignment);
        if (assignment.getStatus() != TrainingActivityAssignmentStatus.ASSIGNED) {
            return hydrate(assignment);
        }
        var now = Instant.now();
        assignment.setStatus(TrainingActivityAssignmentStatus.STARTING);
        assignment.setStartedAt(now);
        assignment.setUpdatedAt(now);
        assignmentRepository.saveAndFlush(assignment);
        enqueueIfAbsent(TrainingActivityAiJobType.FIRST_QUESTION, assignment, null, assignment.getVersion(), "first:" + assignmentId, now);
        return hydrate(assignment);
    }

    @Transactional
    public TrainingActivityAssignment submitAnswer(UUID assignmentId, String answer, UUID answerSubmissionId) {
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
            return hydrate(assignment);
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
        return hydrate(assignment);
    }

    /** Compatibility entrypoint retained for callers compiled before UC-007. */
    public TrainingActivityAssignment answer(UUID assignmentId, String answer) {
        return submitAnswer(assignmentId, answer, UUID.randomUUID());
    }

    @Transactional
    public TrainingActivityAssignment retryTutor(UUID assignmentId) {
        requireOwnedLocked(assignmentId);
        if (tutorJobService == null || !tutorJobService.retryTemporaryFailure(assignmentId)) {
            throw new IllegalStateException("The tutor is not awaiting recovery for this evaluation.");
        }
        return hydrate(requireOwned(assignmentId));
    }

    /** Compatibility transport: completion means the durable command committed, not model completion. */
    public Flux<AnswerStreamEvent> answerStream(UUID assignmentId, String answer) {
        return Flux.just(AnswerStreamEvent.completed(submitAnswer(assignmentId, answer, UUID.randomUUID())));
    }

    @Transactional(readOnly = true)
    public List<EvaluationExchange> readEvaluationTranscript(TrainingActivityAssignment assignment) {
        if (assignment == null || assignment.getId() == null) {
            return List.of();
        }
        return turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(assignment.getId()).stream()
                .filter(turn -> turn.getAnswerText() != null)
                .map(turn -> new EvaluationExchange(turn.getQuestionText(), turn.getAnswerText()))
                .toList();
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
        if (assignment.getStatus().isTerminal() || assignment.getTrainingActivity().getStatus() != TrainingActivityLifecycleStatus.PUBLISHED) {
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

    private TrainingActivityAssignment hydrate(TrainingActivityAssignment assignment) {
        var turns = turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(assignment.getId());
        assignment.setQuestionCount(turns.size());
        assignment.setCurrentQuestion(turns.stream().filter(turn -> turn.getAnswerText() == null)
                .map(TrainingActivityTurn::getQuestionText).reduce((first, second) -> second).orElse(null));
        assignment.setEvaluationTranscript("[]");
        return assignment;
    }

    public record EvaluationExchange(String question, String answer) {}
    public record AnswerStreamEvent(String messageDelta, TrainingActivityAssignment assignment) {
        public static AnswerStreamEvent completed(TrainingActivityAssignment assignment) {
            return new AnswerStreamEvent("", assignment);
        }
        public static AnswerStreamEvent messageDelta(String messageDelta) {
            return new AnswerStreamEvent(messageDelta, null);
        }
    }
}
