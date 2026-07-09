package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wornux.ai.prompt.PromptResources;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.training_activity.AnswerQuality;
import com.wornux.data.entities.training_activity.CoverageStatus;
import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.PedagogicalMove;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.services.training_activity.TrainingAssignmentEvaluationService.EvaluationExchange;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.test.util.ReflectionTestUtils;

class TrainingAssignmentTutorServiceReportValidationTest {

    @Test
    void finalReportFallsBackWhenModelReportOmitsRequiredTeacherSections() {
        var service = new TrainingAssignmentTutorService(
                _ -> response("{\"report\":\"## Síntesis diagnóstica\\nDiagnóstico breve.\"}"),
                promptResources());

        var report = service.finalReport(assignment(), transcript(), success());

        assertThat(report)
                .contains("Reporte de evaluación")
                .contains("## Evidencias observables")
                .contains("## Limitaciones de esta evaluación")
                .contains("## Recomendación docente")
                .contains("## Transcripción")
                .contains("I am not sure.");
    }

    private static PromptResources promptResources() {
        var promptResources = mock(PromptResources.class);
        when(promptResources.reportPrompt()).thenReturn("Instructions: %s\nTitle: %s\nDecision: %s\nEvidence: %s\nLimits: %s\nTranscript:\n%s");
        return promptResources;
    }

    private static TrainingActivityAssignment assignment() {
        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(activity, "title", "Pointers");
        ReflectionTestUtils.setField(activity, "instructions", "Evaluate whether the student understands pointers in C.");

        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());

        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "groupClassMember", member);
        ReflectionTestUtils.setField(assignment, "updatedAt", Instant.now());
        return assignment;
    }

    private static List<EvaluationExchange> transcript() {
        return List.of(new EvaluationExchange("What is a pointer?", "I am not sure."));
    }

    private static AdaptiveTutorDecision success() {
        return new AdaptiveTutorDecision(
                TutorDecisionType.COMPLETE_SUCCESS,
                AnswerQuality.EXCELLENT,
                EvidenceStatus.STRONG_EVIDENCE,
                CoverageStatus.SUFFICIENT,
                PedagogicalMove.COMPLETE_SUCCESSFULLY,
                false,
                List.of("understanding", "example"),
                List.of(),
                false,
                "",
                "Sufficient evidence.");
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
