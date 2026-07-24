package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wornux.ai.prompt.PromptResources;
import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
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

    @Test
    void finalReportPromptLeavesMaxTokensToTheProvider() {
        var capturedPrompt = new AtomicReference<Prompt>();
        var service = new TrainingAssignmentTutorService(prompt -> {
            capturedPrompt.set(prompt);
            return response("""
                    {"evidenceStatus":"STRONG_EVIDENCE","summary":"La respuesta explica el concepto.","strengths":[],"weaknesses":[],"observations":[],"recommendations":[]}
                    """);
        }, promptResources());

        service.generateFinalReport(assignment(), List.of(new TrainingAssignmentTutorService.ReportTurn(
                1, "What is a pointer?", "It stores an address.")), EvidenceStatus.STRONG_EVIDENCE);

        assertThat(capturedPrompt.get().getOptions().getMaxTokens()).isNull();
    }

    @Test
    void finalReportSuppliesTheAuthoritativeEvidenceStatusWhenTheModelOmitsIt() {
        var service = new TrainingAssignmentTutorService(
                _ -> response("""
                        {"summary":"La evidencia es limitada.","strengths":[],"weaknesses":[{"observation":"Necesita ampliar la explicación.","evidenceReferences":[{"turnSequence":1}]}],"observations":[{"observation":"Respondió de forma breve.","evidenceReferences":[{"turnSequence":1}]}],"recommendations":["Pedir una explicación más completa."]}
                        """),
                promptResources());

        var report = service.generateFinalReport(assignment(), List.of(new TrainingAssignmentTutorService.ReportTurn(
                1, "What is a pointer?", "I am not sure.")), EvidenceStatus.WEAK_EVIDENCE);

        assertThat(report.evidenceStatus()).isEqualTo(EvidenceStatus.WEAK_EVIDENCE);
        assertThat(report.summary()).isEqualTo("La evidencia es limitada.");
        assertThat(report.strengths()).isEmpty();
        assertThat(report.weaknesses()).containsExactly(new FinalReportCandidate.ReportFinding(
                "Necesita ampliar la explicación.", List.of(new FinalReportCandidate.EvidenceReference(1, null, null))));
        assertThat(report.observations()).containsExactly(new FinalReportCandidate.ReportFinding(
                "Respondió de forma breve.", List.of(new FinalReportCandidate.EvidenceReference(1, null, null))));
        assertThat(report.recommendations()).containsExactly("Pedir una explicación más completa.");
    }

    @Test
    void finalReportOverridesAConflictingModelEvidenceStatusWithoutChangingContent() {
        var service = new TrainingAssignmentTutorService(
                _ -> response("""
                        {"evidenceStatus":"STRONG_EVIDENCE","summary":"La evidencia es limitada.","strengths":[],"weaknesses":[{"observation":"Necesita ampliar la explicación.","evidenceReferences":[{"turnSequence":1}]}],"observations":[{"observation":"Respondió de forma breve.","evidenceReferences":[{"turnSequence":1}]}],"recommendations":["Pedir una explicación más completa."]}
                        """),
                promptResources());

        var report = service.generateFinalReport(assignment(), List.of(new TrainingAssignmentTutorService.ReportTurn(
                1, "What is a pointer?", "I am not sure.")), EvidenceStatus.WEAK_EVIDENCE);

        assertThat(report.evidenceStatus()).isEqualTo(EvidenceStatus.WEAK_EVIDENCE);
        assertThat(report.summary()).isEqualTo("La evidencia es limitada.");
        assertThat(report.strengths()).isEmpty();
        assertThat(report.weaknesses()).containsExactly(new FinalReportCandidate.ReportFinding(
                "Necesita ampliar la explicación.", List.of(new FinalReportCandidate.EvidenceReference(1, null, null))));
        assertThat(report.observations()).containsExactly(new FinalReportCandidate.ReportFinding(
                "Respondió de forma breve.", List.of(new FinalReportCandidate.EvidenceReference(1, null, null))));
        assertThat(report.recommendations()).containsExactly("Pedir una explicación más completa.");
    }

    @Test
    void finalReportBuildsTheExactNoEvidenceCandidateWithoutCallingTheModel() {
        var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
        var service = new TrainingAssignmentTutorService(chatModel, promptResources());

        var report = service.generateFinalReport(assignment(), List.of(), EvidenceStatus.NO_EVIDENCE);

        assertThat(report).isEqualTo(new FinalReportCandidate(
                EvidenceStatus.NO_EVIDENCE,
                "No hay evidencia observable suficiente para alcanzar una conclusión defendible.",
                List.of(),
                List.of(),
                List.of(),
                List.of("Repetir con una pregunta más acotada y solicitar razonamiento o un ejemplo concreto.")));
        org.mockito.Mockito.verifyNoInteractions(chatModel);
    }

    @Test
    void finalReportUsesTheModelForEvidenceStatusesThatRequireDiagnosticContent() {
        var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
        when(chatModel.call(org.mockito.ArgumentMatchers.<org.springframework.ai.chat.prompt.Prompt>any())).thenReturn(response("""
                {"evidenceStatus":"STRONG_EVIDENCE","summary":"La respuesta explica el concepto con un ejemplo.","strengths":[{"observation":"Explica el concepto.","evidenceReferences":[{"turnSequence":1}]}],"weaknesses":[],"observations":[],"recommendations":["Pedir una justificación adicional."]}
                """));
        var service = new TrainingAssignmentTutorService(chatModel, promptResources());

        for (var evidenceStatus : List.of(
                EvidenceStatus.WEAK_EVIDENCE, EvidenceStatus.PARTIAL_EVIDENCE, EvidenceStatus.STRONG_EVIDENCE)) {
            assertThat(service.generateFinalReport(assignment(), List.of(new TrainingAssignmentTutorService.ReportTurn(
                    1, "What is a pointer?", "I am not sure.")), evidenceStatus).evidenceStatus())
                    .isEqualTo(evidenceStatus);
        }

        org.mockito.Mockito.verify(chatModel, org.mockito.Mockito.times(3))
                .call(org.mockito.ArgumentMatchers.<org.springframework.ai.chat.prompt.Prompt>any());
    }

    @Test
    void finalReportRejectsMissingAuthoritativeStatusBeforeCallingTheModel() {
        var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
        var service = new TrainingAssignmentTutorService(chatModel, promptResources());

        assertThat(catchThrowableOfType(
                        () -> service.generateFinalReport(assignment(), List.of(), null), FinalReportAuthorityException.class))
                .satisfies(exception -> assertThat(exception.getMessage())
                        .isEqualTo("Final report requires an authoritative evidence status."));
        org.mockito.Mockito.verifyNoInteractions(chatModel);
    }

    @Test
    void finalReportClassifiesAnEmptyResponseWithoutExposingItsContent() {
        var service = new TrainingAssignmentTutorService(_ -> response(""), promptResources());

        var exception = catchThrowableOfType(
                () -> service.generateFinalReport(assignment(), List.of(), EvidenceStatus.STRONG_EVIDENCE),
                AdaptiveTutorModelOutputException.class);

        assertThat(exception.failureCode()).isEqualTo("EMPTY_FINAL_REPORT_OUTPUT");
    }

    @Test
    void finalReportClassifiesMalformedOutputConversion() {
        var service = new TrainingAssignmentTutorService(_ -> response("{not valid JSON}"), promptResources());

        var exception = catchThrowableOfType(
                () -> service.generateFinalReport(assignment(), List.of(), EvidenceStatus.STRONG_EVIDENCE),
                AdaptiveTutorModelOutputException.class);

        assertThat(exception.failureCode()).isEqualTo("MALFORMED_FINAL_REPORT_OUTPUT");
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
