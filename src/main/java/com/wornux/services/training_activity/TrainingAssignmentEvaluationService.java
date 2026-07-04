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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TrainingAssignmentEvaluationService {

    private static final TypeReference<
        List<EvaluationExchange>
    > TRANSCRIPT_TYPE = new TypeReference<>() {};

    private final TrainingActivityAssignmentRepository assignmentRepository;
    private final ActiveAcademicContextResolver contextResolver;
    private final TrainingAssignmentTutorService tutorService;
    private final JsonMapper jsonMapper;

    public TrainingAssignmentEvaluationService(
        TrainingActivityAssignmentRepository assignmentRepository,
        ActiveAcademicContextResolver contextResolver,
        TrainingAssignmentTutorService tutorService,
        JsonMapper jsonMapper
    ) {
        this.assignmentRepository = assignmentRepository;
        this.contextResolver = contextResolver;
        this.tutorService = tutorService;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public TrainingActivityAssignment getForCurrentStudent(UUID assignmentId) {
        var context = contextResolver.requireCurrent();
        if (context.groupClassKind() != GroupClassMemberKind.STUDENT) {
            throw new SecurityException(
                "Only students can complete assigned evaluations."
            );
        }
        return assignmentRepository
            .findWithTrainingActivityById(assignmentId)
            .filter(assignment ->
                context
                    .groupClassMemberId()
                    .equals(assignment.getGroupClassMember().getId())
            )
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Unknown training assignment %s".formatted(assignmentId)
                )
            );
    }

    @Transactional
    public TrainingActivityAssignment start(UUID assignmentId) {
        var assignment = getForCurrentStudent(assignmentId);
        ensureAnswerable(assignment);
        if (
            assignment.getStatus() == TrainingActivityAssignmentStatus.ASSIGNED
        ) {
            assignment.setStatus(TrainingActivityAssignmentStatus.STARTED);
            assignment.setStartedAt(Instant.now());
            assignment.setCurrentQuestion(
                tutorService.firstQuestion(assignment)
            );
            assignment.setQuestionCount(1);
            assignment.setUpdatedAt(Instant.now());
        }
        return assignmentRepository.save(assignment);
    }

    @Transactional
    public TrainingActivityAssignment answer(UUID assignmentId, String answer) {
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException(
                "Evaluation answers cannot be blank."
            );
        }
        var assignment = getForCurrentStudent(assignmentId);
        ensureAnswerable(assignment);
        if (
            assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED
        ) {
            return assignment;
        }
        if (
            assignment.getCurrentQuestion() == null ||
            assignment.getCurrentQuestion().isBlank()
        ) {
            start(assignmentId);
            assignment = getForCurrentStudent(assignmentId);
        }

        var transcript = readTranscript(assignment.getEvaluationTranscript());
        transcript.add(
            new EvaluationExchange(
                assignment.getCurrentQuestion(),
                answer.trim()
            )
        );
        assignment.setEvaluationTranscript(writeTranscript(transcript));

        var nextQuestion = tutorService.nextQuestion(assignment, answer);
        if (nextQuestion == null) {
            assignment.setStatus(TrainingActivityAssignmentStatus.SUBMITTED);
            assignment.setSubmittedAt(Instant.now());
            assignment.setCurrentQuestion(null);
            assignment.setFinalReport(
                tutorService.finalReport(assignment, transcriptMarkdown(transcript))
            );
        } else {
            assignment.setStatus(TrainingActivityAssignmentStatus.STARTED);
            assignment.setCurrentQuestion(nextQuestion);
            assignment.setQuestionCount(assignment.getQuestionCount() + 1);
        }
        assignment.setUpdatedAt(Instant.now());
        return assignmentRepository.save(assignment);
    }

    private void ensureAnswerable(TrainingActivityAssignment assignment) {
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            return;
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

    private String writeTranscript(List<EvaluationExchange> transcript) {
        try {
            return jsonMapper.writeValueAsString(transcript);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                "Could not write training assignment transcript.",
                exception
            );
        }
    }

    private String transcriptMarkdown(List<EvaluationExchange> transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return "No hay respuestas registradas.";
        }
        var markdown = new StringBuilder();
        for (var index = 0; index < transcript.size(); index++) {
            var exchange = transcript.get(index);
            markdown.append("### Pregunta ").append(index + 1).append("\n");
            markdown.append(exchange.question()).append("\n\n");
            markdown.append("**Respuesta del estudiante:**  \n");
            markdown.append(exchange.answer()).append("\n\n");
        }
        return markdown.toString().trim();
    }

    public record EvaluationExchange(String question, String answer) {}
}
