package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wornux.ai.prompt.PromptResources;
import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
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
    void finalReportUsesTheStructuredCandidateContract() {
        var service = new TrainingAssignmentTutorService(
                _ -> response("""
                        {"evidenceStatus":"STRONG_EVIDENCE","summary":"La respuesta explica el concepto con un ejemplo.","strengths":[{"observation":"Explica el concepto.","evidenceReferences":[{"turnSequence":1,"answerExcerpt":"I am not sure."}]}],"weaknesses":[{"observation":"Puede ampliar la justificación.","evidenceReferences":[{"turnSequence":1}]}],"observations":[{"observation":"La respuesta contiene un ejemplo.","evidenceReferences":[{"turnSequence":1}]}],"recommendations":["Pedir una justificación adicional."]}
                        """),
                promptResources());

        var report = service.generateFinalReport(assignment(), List.of(new TrainingAssignmentTutorService.ReportTurn(
                1, "What is a pointer?", "I am not sure.")), EvidenceStatus.STRONG_EVIDENCE);

        assertThat(report)
                .extracting(FinalReportCandidate::evidenceStatus, FinalReportCandidate::summary)
                .containsExactly(EvidenceStatus.STRONG_EVIDENCE, "La respuesta explica el concepto con un ejemplo.");
        assertThat(report.strengths()).singleElement().satisfies(finding ->
                assertThat(finding.evidenceReferences()).extracting(FinalReportCandidate.EvidenceReference::turnSequence)
                        .containsExactly(1));
    }

    private static PromptResources promptResources() {
        var promptResources = mock(PromptResources.class);
        when(promptResources.reportPrompt()).thenReturn(
                "Schema: %s\nInstructions: %s\nTitle: %s\nEvidence: %s\nTranscript:\n%s");
        return promptResources;
    }

    private static TrainingActivityAssignment assignment() {
        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(activity, "title", "Pointers");
        ReflectionTestUtils.setField(activity, "instructions", "Evaluate whether the student understands pointers in C.");

        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "updatedAt", Instant.now());
        return assignment;
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
