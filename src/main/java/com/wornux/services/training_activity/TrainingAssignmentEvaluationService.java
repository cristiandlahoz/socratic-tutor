package com.wornux.services.training_activity;

import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.services.context.ActiveAcademicContextResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TrainingAssignmentEvaluationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrainingAssignmentEvaluationService.class);

    private static final TypeReference<
        List<EvaluationExchange>
    > TRANSCRIPT_TYPE = new TypeReference<>() {};

    private final TrainingActivityAssignmentRepository assignmentRepository;
    private final ActiveAcademicContextResolver contextResolver;
    private final TrainingAssignmentTutorService tutorService;
    private final JsonMapper jsonMapper;
    private final TrainingAssignmentDecisionPersistenceService decisionPersistenceService;

    public TrainingAssignmentEvaluationService(
        TrainingActivityAssignmentRepository assignmentRepository,
        ActiveAcademicContextResolver contextResolver,
        TrainingAssignmentTutorService tutorService,
        JsonMapper jsonMapper,
        TrainingAssignmentDecisionPersistenceService decisionPersistenceService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.contextResolver = contextResolver;
        this.tutorService = tutorService;
        this.jsonMapper = jsonMapper;
        this.decisionPersistenceService = decisionPersistenceService;
    }

    @Transactional(readOnly = true)
    public TrainingActivityAssignment getForCurrentStudent(UUID assignmentId) {
        return getForStudent(assignmentId, requireCurrentStudentGroupClassMemberId());
    }

    @Transactional
    public TrainingActivityAssignment start(UUID assignmentId) {
        var assignment = getForCurrentStudent(assignmentId);
        ensureAnswerable(assignment);
        if (
            assignment.getStatus() == TrainingActivityAssignmentStatus.ASSIGNED
        ) {
            AdaptiveTutorDecision decision;
            try {
                decision = tutorService.firstDecision(assignment);
                if (decision.type() != TutorDecisionType.QUESTION) {
                    throw new IllegalStateException("The adaptive tutor must start with a question.");
                }
            }
            catch (RuntimeException exception) {
                LOGGER.warn(
                        "Training assignment start failed before the first question was persisted: assignmentId={} trainingActivityId={} status={} safeBrowserEnabled={} safeBrowserSessionActive={} model={} reason={}",
                        assignment.getId(),
                        assignment.getTrainingActivity() == null ? null : assignment.getTrainingActivity().getId(),
                        assignment.getStatus(),
                        assignment.getTrainingActivity() != null && assignment.getTrainingActivity().isSafeBrowserEnabled(),
                        assignment.isSafeBrowserSessionActive(),
                        tutorService.currentModelName(),
                        exception.getMessage(),
                        exception);
                throw new AdaptiveTutorStartUnavailableException(exception);
            }
            assignment.setStatus(TrainingActivityAssignmentStatus.STARTED);
            assignment.setStartedAt(Instant.now());
            assignment.setCurrentQuestion(decision.questionText());
            assignment.setQuestionCount(1);
            decisionPersistenceService.applyDecisionMetadata(assignment, decision);
            assignment.setUpdatedAt(Instant.now());
        }
        return assignmentRepository.save(assignment);
    }

    public TrainingActivityAssignment answer(UUID assignmentId, String answer) {
        var preparedAnswer = prepareAnswer(assignmentId, answer);
        if (preparedAnswer.alreadySubmitted()) {
            return preparedAnswer.assignment();
        }
        var decision = tutorService.nextDecision(preparedAnswer.assignment(), preparedAnswer.answer(), preparedAnswer.transcript());
        return persistDecision(preparedAnswer, decision);
    }

    public Flux<AnswerStreamEvent> answerStream(UUID assignmentId, String answer) {
        var preparedAnswer = prepareAnswer(assignmentId, answer);
        if (preparedAnswer.alreadySubmitted()) {
            return Flux.just(AnswerStreamEvent.completed(preparedAnswer.assignment()));
        }
        return tutorService.nextDecisionStream(preparedAnswer.assignment(), preparedAnswer.answer(), preparedAnswer.transcript())
                .concatMap(event -> {
                    if (!event.isCompletion()) {
                        return event.textDelta().isBlank()
                                ? Flux.empty()
                                : Flux.just(AnswerStreamEvent.messageDelta(event.textDelta()));
                    }
                    return Mono.fromCallable(() -> AnswerStreamEvent.completed(persistDecision(preparedAnswer, event.decision())))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flux();
                });
    }

    private boolean isTerminalDecision(AdaptiveTutorDecision decision) {
        return decision != null
                && (decision.type() == TutorDecisionType.COMPLETE_SUCCESS
                || decision.type() == TutorDecisionType.COMPLETE_INSUFFICIENT_EVIDENCE);
    }

    private TrainingActivityAssignment persistDecision(PreparedAnswer preparedAnswer, AdaptiveTutorDecision decision) {
        var finalReport = isTerminalDecision(decision)
                ? tutorService.finalReport(preparedAnswer.assignment(), preparedAnswer.transcript(), decision)
                : null;
        return decisionPersistenceService.applyDecision(
                preparedAnswer.assignmentId(),
                preparedAnswer.studentGroupClassMemberId(),
                preparedAnswer.transcript(),
                decision,
                finalReport);
    }

    private PreparedAnswer prepareAnswer(UUID assignmentId, String answer) {
        var studentGroupClassMemberId = requireCurrentStudentGroupClassMemberId();
        var assignment = getForStudent(assignmentId, studentGroupClassMemberId);
        ensureAnswerable(assignment);
        var normalizedAnswer = normalizeSubmittedAnswer(answer);
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            return new PreparedAnswer(assignment, assignmentId, studentGroupClassMemberId,
                    normalizedAnswer, readTranscript(assignment.getEvaluationTranscript()), true);
        }
        if (
            assignment.getCurrentQuestion() == null ||
            assignment.getCurrentQuestion().isBlank()
        ) {
            start(assignmentId);
            assignment = getForStudent(assignmentId, studentGroupClassMemberId);
        }

        var transcript = readTranscript(assignment.getEvaluationTranscript());
        transcript.add(
            new EvaluationExchange(
                assignment.getCurrentQuestion(),
                normalizedAnswer
            )
        );
        return new PreparedAnswer(assignment, assignmentId, studentGroupClassMemberId, normalizedAnswer, transcript, false);
    }

    private String normalizeSubmittedAnswer(String answer) {
        return answer == null ? "" : answer.trim();
    }

    private UUID requireCurrentStudentGroupClassMemberId() {
        var context = contextResolver.requireCurrent();
        if (context.groupClassKind() != GroupClassMemberKind.STUDENT) {
            throw new SecurityException(
                "Only students can complete assigned evaluations."
            );
        }
        return context.groupClassMemberId();
    }

    private TrainingActivityAssignment getForStudent(UUID assignmentId, UUID studentGroupClassMemberId) {
        return assignmentRepository
            .findWithTrainingActivityById(assignmentId)
            .filter(assignment -> studentGroupClassMemberId.equals(assignment.getGroupClassMember().getId()))
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Unknown training assignment %s".formatted(assignmentId)
                )
            );
    }

    private void ensureAnswerable(TrainingActivityAssignment assignment) {
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            return;
        }
        if (assignment.getStatus().isTerminal()) {
            throw new IllegalStateException("The evaluation assignment has ended.");
        }
        if (assignment.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED) {
            throw new IllegalStateException("The evaluation window has ended.");
        }
        if (assignment.isSafeBrowserLocked()) {
            throw new IllegalStateException(
                "Safe Browser Mode was interrupted. Ask your professor to review this assignment."
            );
        }
        if (
            assignment.getTrainingActivity().isSafeBrowserEnabled() &&
            !assignment.isSafeBrowserSessionActive()
        ) {
            throw new IllegalStateException(
                "Safe Browser Mode must be active before answering."
            );
        }
    }

    public List<EvaluationExchange> readEvaluationTranscript(
        TrainingActivityAssignment assignment
    ) {
        return readTranscript(assignment.getEvaluationTranscript());
    }

    private List<EvaluationExchange> readTranscript(String transcriptJson) {
        if (transcriptJson == null || transcriptJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(
                jsonMapper.readValue(transcriptJson, TRANSCRIPT_TYPE)
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                "Could not read training assignment transcript.",
                exception
            );
        }
    }

    public record EvaluationExchange(String question, String answer) {}

    public record AnswerStreamEvent(String messageDelta, TrainingActivityAssignment assignment) {

        public static AnswerStreamEvent messageDelta(String messageDelta) {
            return new AnswerStreamEvent(messageDelta, null);
        }

        public static AnswerStreamEvent completed(TrainingActivityAssignment assignment) {
            return new AnswerStreamEvent("", assignment);
        }
    }

    private record PreparedAnswer(
            TrainingActivityAssignment assignment,
            UUID assignmentId,
            UUID studentGroupClassMemberId,
            String answer,
            List<EvaluationExchange> transcript,
            boolean alreadySubmitted) {}
}
