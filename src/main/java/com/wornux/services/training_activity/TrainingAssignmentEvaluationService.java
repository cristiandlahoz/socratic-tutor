package com.wornux.services.training_activity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.services.context.ActiveAcademicContextResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingAssignmentEvaluationService {

    private static final TypeReference<List<EvaluationExchange>> TRANSCRIPT_TYPE = new TypeReference<>() {};

    private final TrainingActivityAssignmentRepository assignmentRepository;
    private final ActiveAcademicContextResolver contextResolver;
    private final TrainingAssignmentTutorService tutorService;
    private final ObjectMapper objectMapper;

    public TrainingAssignmentEvaluationService(
            TrainingActivityAssignmentRepository assignmentRepository,
            ActiveAcademicContextResolver contextResolver,
            TrainingAssignmentTutorService tutorService,
            ObjectMapper objectMapper) {
        this.assignmentRepository = assignmentRepository;
        this.contextResolver = contextResolver;
        this.tutorService = tutorService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TrainingActivityAssignment getForCurrentStudent(UUID assignmentId) {
        var context = contextResolver.requireCurrent();
        if (context.groupClassRole() != GroupClassMemberRole.STUDENT) {
            throw new SecurityException("Only students can complete assigned evaluations.");
        }
        return assignmentRepository.findWithTrainingActivityById(assignmentId)
                .filter(assignment -> context.groupClassMemberId().equals(assignment.getGroupClassMember().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown training assignment %s".formatted(assignmentId)));
    }

    @Transactional
    public TrainingActivityAssignment start(UUID assignmentId) {
        var assignment = getForCurrentStudent(assignmentId);
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.ASSIGNED) {
            assignment.setStatus(TrainingActivityAssignmentStatus.STARTED);
            assignment.setStartedAt(Instant.now());
            assignment.setCurrentQuestion(tutorService.firstQuestion(assignment));
            assignment.setQuestionCount(1);
            assignment.setUpdatedAt(Instant.now());
        }
        return assignmentRepository.save(assignment);
    }

    @Transactional
    public TrainingActivityAssignment answer(UUID assignmentId, String answer) {
        var assignment = getForCurrentStudent(assignmentId);
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            return assignment;
        }
        if (assignment.getCurrentQuestion() == null || assignment.getCurrentQuestion().isBlank()) {
            start(assignmentId);
            assignment = getForCurrentStudent(assignmentId);
        }

        var transcript = readTranscript(assignment.getEvaluationTranscript());
        transcript.add(new EvaluationExchange(assignment.getCurrentQuestion(), answer.trim()));
        assignment.setEvaluationTranscript(writeTranscript(transcript));

        var nextQuestion = tutorService.nextQuestion(assignment, answer);
        if (nextQuestion == null) {
            assignment.setStatus(TrainingActivityAssignmentStatus.SUBMITTED);
            assignment.setSubmittedAt(Instant.now());
            assignment.setCurrentQuestion(null);
            assignment.setFinalReport(tutorService.finalReport(assignment));
        }
        else {
            assignment.setStatus(TrainingActivityAssignmentStatus.STARTED);
            assignment.setCurrentQuestion(nextQuestion);
            assignment.setQuestionCount(assignment.getQuestionCount() + 1);
        }
        assignment.setUpdatedAt(Instant.now());
        return assignmentRepository.save(assignment);
    }

    private List<EvaluationExchange> readTranscript(String transcriptJson) {
        if (transcriptJson == null || transcriptJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(transcriptJson, TRANSCRIPT_TYPE));
        }
        catch (JsonProcessingException exception) {
            return new ArrayList<>();
        }
    }

    private String writeTranscript(List<EvaluationExchange> transcript) {
        try {
            return objectMapper.writeValueAsString(transcript);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not persist evaluation transcript.", exception);
        }
    }

    public record EvaluationExchange(String question, String answer) {}
}
