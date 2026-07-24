package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wornux.ai.prompt.PromptResources;
import com.wornux.data.entities.training_activity.AnswerQuality;
import com.wornux.data.entities.training_activity.CoverageStatus;
import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.PedagogicalMove;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

class TrainingAssignmentTutorServiceTest {

    @Test
    void firstDecisionFallsBackWhenModelReturnsEmptyResponseInDevelopment() {
        var service = localFallbackEnabledService(modelReturning("   "));

        var decision = service.firstDecision(assignment());

        assertFallbackDecision(decision);
    }

    @Test
    void firstDecisionFailsControlledWithoutFallbackInProduction() {
        var service = new TrainingAssignmentTutorService(modelReturning("not-json"), promptResources());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.firstDecision(assignment())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No fue posible continuar la tutoría");
    }

    @Test
    void nextDecisionFallsBackWhenModelReturnsEmptyResponse() {
        var service = localFallbackEnabledService(modelReturning("   "));

        var decision = service.nextDecision(assignment(), "I am not sure.", transcript());

        assertFallbackDecision(decision);
    }

    @Test
    void nextDecisionFallsBackWhenModelReturnsMalformedResponse() {
        var service = localFallbackEnabledService(modelReturning("not-json"));

        var decision = service.nextDecision(assignment(), "I am not sure.", transcript());

        assertFallbackDecision(decision);
    }

    @Test
    void nextDecisionSelectsTheFirstUsableGenerationInsteadOfMisclassifyingTheFirstBlankOne() {
        var service = new TrainingAssignmentTutorService(
                modelReturningGenerations("   ", questionJson("¿Qué evidencia respalda esa conclusión?")), promptResources());

        var decision = service.nextDecision(assignment(), "Creo que es correcto.", transcript());

        assertThat(decision.questionText()).isEqualTo("¿Qué evidencia respalda esa conclusión?");
    }

    @Test
    void lengthFinishedResponseUsesTheTruncationFailureCode() {
        var service = new TrainingAssignmentTutorService(
                _ -> response("{\"type\":\"QUESTION\"}", "length"), promptResources());

        var failure = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.nextDecision(assignment(), "Creo que es correcto.", transcript()));

        assertThat(failure).isInstanceOf(AdaptiveTutorModelOutputException.class);
        assertThat(((AdaptiveTutorModelOutputException) failure).failureCode()).isEqualTo("MODEL_OUTPUT_TRUNCATED");
    }

    @Test
    void nextDecisionCompletesInsufficientEvidenceAfterFallbackAlreadyUsed() {
        var service = localFallbackEnabledService(modelReturning("   "));
        var assignment = assignment();

        var fallbackQuestion = service.nextDecision(assignment, "I am not sure.", transcript(), 1, "Initial fallback question");

        var decision = service.nextDecision(assignment, "I need another hint.", transcript(), 2, fallbackQuestion.questionText());

        assertThat(decision.type()).isEqualTo(TutorDecisionType.COMPLETE_INSUFFICIENT_EVIDENCE);
        assertThat(decision.shouldContinue()).isFalse();
        assertThat(decision.questionText()).isBlank();
        assertThat(decision.reason()).contains("Local development fallback closed the evaluation");
        assertThat(decision.pedagogicalMove()).isEqualTo(PedagogicalMove.COMPLETE_WITH_INSUFFICIENT_EVIDENCE);
    }

    @Test
    void nextDecisionStreamEmitsQuestionTextWithoutLeakingJson() {
        var question = "¿Puedes justificarlo con un ejemplo concreto?";
        var service = localFallbackEnabledService(streamingModel(
                "{\"type\":\"QUESTION\",\"answerQuality\":\"GOOD\",\"evidenceStatus\":\"PARTIAL_EVIDENCE\",",
                "\"coverageStatus\":\"PARTIAL\",\"pedagogicalMove\":\"ASK_FOR_CLARITY\",\"shouldContinue\":true,",
                "\"coveredInstructionAspects\":[],\"missingInstructionAspects\":[\"example\"],\"unproductivePatternDetected\":false,",
                "\"questionText\":\"¿Puedes justificarlo con un ejemplo concreto?\",\"reason\":\"Needs one more question.\"}"));

        var events = service.nextDecisionStream(assignment(), "I am not sure.", transcript()).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.stream().map(TrainingAssignmentTutorService.AdaptiveTutorStreamEvent::textDelta).reduce("", String::concat))
                .isEqualTo(question);
        assertThat(events.getLast().decision()).isNotNull();
        assertThat(events.getLast().decision().questionText()).isEqualTo(question);
    }

    @Test
    void nextDecisionPromptUsesVariationSeedAndRecentEvidenceSummary() {
        var capturedPrompt = new AtomicReference<Prompt>();
        var service = localFallbackEnabledService(new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                capturedPrompt.set(prompt);
                return response(questionJson("¿Qué cambiaría si dereferencias ese puntero?"));
            }
        });

        service.nextDecision(assignment(), "Porque guarda una dirección y *p permite leer el valor.", transcriptWithHistory());

        var promptText = capturedPrompt.get().getInstructions().getLast().getText();
        assertThat(promptText).contains("Variation seed:");
        assertThat(promptText).contains("Señales simples de la respuesta:");
        assertThat(promptText).contains("Evidencia reciente:");
        assertThat(promptText).contains("Evidencia observable que debes respetar:");
        assertThat(promptText).contains("### Pregunta 2");
        assertThat(promptText).contains("### Pregunta 3");
        assertThat(promptText).doesNotContain("Grounding:");
    }

    @Test
    void decisionPromptLeavesMaxTokensToTheProvider() {
        var capturedPrompt = new AtomicReference<Prompt>();
        var service = new TrainingAssignmentTutorService(prompt -> {
            capturedPrompt.set(prompt);
            return response(questionJson("¿Qué evidencia respalda esa conclusión?"));
        }, promptResources());

        service.firstDecision(assignment());

        assertThat(capturedPrompt.get().getOptions().getMaxTokens()).isNull();
    }

    @Test
    void doesNotUseHardcodedFallbackQuestionInProduction() {
        var service = new TrainingAssignmentTutorService(modelReturning("not-json"), promptResources());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.nextDecision(assignment(), "No estoy seguro.", transcript())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No fue posible continuar la tutoría");
    }

    @Test
    void doesNotInventSyntaxErrorForValidCLoop() {
        var service = new TrainingAssignmentTutorService(modelReturning(questionJson(
                "Observa esta variante:\n\n```c\nfor (int i = 0; i < 3; i++)\n    printf(\"%d\", i);\n```\n\n¿Dónde está el error de sintaxis y qué token lo rompe?")), promptResources());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.nextDecision(assignment(), "Compila y recorre 0, 1 y 2.", transcript())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No fue posible continuar la tutoría");
    }

    @Test
    void rejectsFalsePremiseTutorDecision() {
        var correctionTranscript = List.of(
                new TrainingAssignmentEvaluationService.EvaluationExchange(
                        "Observa esta variante:\n\n```c\nfor (int i = 0; i < 3; i++)\n    printf(\"%d\", i);\n```\n\n¿Dónde está el problema?",
                        "No tiene error; compila incluso sin llaves si solo controla una instrucción."));
        var service = new TrainingAssignmentTutorService(modelReturning(questionJson(
                "El primer for no compila por no tener llaves. ¿Qué línea está mal?")), promptResources());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.nextDecision(assignment(), "Sigue compilando.", correctionTranscript)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No fue posible continuar la tutoría");
    }

    @Test
    void questionWithCodeUsesFencedMarkdown() {
        var question = "Observa esta variante:\n\n```c\nfor (int i = 0; i < 3; i++)\n    printf(\"%d\", i);\n```\n\n¿Cuántas veces se ejecuta printf y por qué?";
        var service = new TrainingAssignmentTutorService(modelReturning(questionJson(question)), promptResources());

        var decision = service.nextDecision(assignment(), "Creo que tres veces.", transcript());

        assertThat(decision.questionText()).contains("```c");
        assertThat(decision.questionText()).contains("printf");
    }

    @Test
    void studentCorrectionIsTreatedAsEvidence() {
        var capturedPrompt = new AtomicReference<Prompt>();
        var transcript = List.of(
                new TrainingAssignmentEvaluationService.EvaluationExchange(
                        "Observa esta variante:\n\n```c\nfor (int i = 0; i < 3; i++)\n    printf(\"%d\", i);\n```\n\n¿Dónde está el problema?",
                        "No tiene error; compila y printf se ejecuta tres veces, y aun sin llaves compila si solo hay una instrucción."));
        var service = localFallbackEnabledService(new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                capturedPrompt.set(prompt);
                return response(questionJson("¿Qué cambia si el cuerpo del for tiene dos instrucciones?"));
            }
        });

        service.nextDecision(assignment(), "No tiene error; compila y printf se ejecuta tres veces, y aun sin llaves compila si solo hay una instrucción.", transcript);

        assertThat(capturedPrompt.get().getInstructions().getLast().getText())
                .contains("El estudiante corrigió explícitamente una premisa incorrecta")
                .contains("sin llaves puede compilar");
    }

    private static void assertFallbackDecision(AdaptiveTutorDecision decision) {
        assertThat(decision.type()).isEqualTo(TutorDecisionType.QUESTION);
        assertThat(decision.questionText()).contains("Modo de desarrollo");
        assertThat(decision.answerQuality()).isEqualTo(AnswerQuality.TOO_VAGUE);
        assertThat(decision.evidenceStatus()).isEqualTo(EvidenceStatus.WEAK_EVIDENCE);
        assertThat(decision.coverageStatus()).isEqualTo(CoverageStatus.WEAK);
        assertThat(decision.pedagogicalMove()).isEqualTo(PedagogicalMove.ASK_FOR_CLARITY);
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
        ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.WAITING_FOR_ANSWER);
        return assignment;
    }

    private static List<TrainingAssignmentEvaluationService.EvaluationExchange> transcript() {
        return List.of(new TrainingAssignmentEvaluationService.EvaluationExchange("What is a pointer?", "I am not sure."));
    }

    private static List<TrainingAssignmentEvaluationService.EvaluationExchange> transcriptWithHistory() {
        return List.of(
                new TrainingAssignmentEvaluationService.EvaluationExchange("Q1", "A1"),
                new TrainingAssignmentEvaluationService.EvaluationExchange("Q2", "A2"),
                new TrainingAssignmentEvaluationService.EvaluationExchange("Q3", "A3"));
    }

    private static ChatModel modelReturning(String text) {
        return _ -> response(text);
    }

    private static ChatModel modelReturningGenerations(String... texts) {
        return _ -> new ChatResponse(java.util.Arrays.stream(texts)
                .map(text -> new Generation(new AssistantMessage(text)))
                .toList());
    }

    private static PromptResources promptResources() {
        return promptResources("Instrucción: %s\nTítulo: %s\nDecisión: %s\nEvidencia: %s\nLímites: %s\nTranscript:\n%s");
    }

    private static PromptResources promptResources(String reportPrompt) {
        var promptResources = mock(PromptResources.class);
        when(promptResources.adaptiveTutorSystem()).thenReturn("Return only valid JSON.");
        when(promptResources.adaptivePrompt()).thenReturn("""
                Formato JSON requerido:\n%s\nTítulo: %s\nInstrucciones: %s\nEstado: %s\nTurno: %d\nPregunta: %s\nRespuesta: %s\nHistorial completo:\n%s\nEvidencia reciente: %s\nEvidencia observable que debes respetar:\n%s\nVariation seed: %s\nSeñales simples de la respuesta: %s\nAperturas: %s
                """);
        when(promptResources.fallbackQuestion()).thenReturn(
                "Modo de desarrollo: pide una evidencia concreta del tema y una breve justificación en la misma respuesta.");
        when(promptResources.reportPrompt()).thenReturn(reportPrompt);
        return promptResources;
    }

    private static TrainingAssignmentTutorService localFallbackEnabledService(ChatModel chatModel) {
        var service = new TrainingAssignmentTutorService(chatModel, promptResources());
        ReflectionTestUtils.setField(service, "allowLocalFallback", true);
        return service;
    }

    private static ChatModel streamingModel(String... chunks) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return response(String.join("", chunks));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.fromArray(chunks).map(TrainingAssignmentTutorServiceTest::response);
            }
        };
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatResponse response(String text, String finishReason) {
        return new ChatResponse(List.of(new Generation(
                new AssistantMessage(text), ChatGenerationMetadata.builder().finishReason(finishReason).build())));
    }

    private static String questionJson(String question) {
        return """
                {"type":"QUESTION","answerQuality":"GOOD","evidenceStatus":"PARTIAL_EVIDENCE","coverageStatus":"PARTIAL","pedagogicalMove":"ASK_FOR_CLARITY","shouldContinue":true,"coveredInstructionAspects":[],"missingInstructionAspects":["example"],"unproductivePatternDetected":false,"questionText":%s,"reason":"Needs one more question."}
                """.formatted(jsonString(question));
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static AdaptiveTutorDecision success() {
        return new AdaptiveTutorDecision(
                TutorDecisionType.COMPLETE_SUCCESS,
                AnswerQuality.EXCELLENT,
                EvidenceStatus.STRONG_EVIDENCE,
                CoverageStatus.SUFFICIENT,
                PedagogicalMove.COMPLETE_SUCCESSFULLY,
                false,
                List.of("understanding"),
                List.of(),
                false,
                "",
                "Sufficient evidence.");
    }

    private static AdaptiveTutorDecision insufficientEvidence() {
        return new AdaptiveTutorDecision(
                TutorDecisionType.COMPLETE_INSUFFICIENT_EVIDENCE,
                AnswerQuality.TOO_VAGUE,
                EvidenceStatus.WEAK_EVIDENCE,
                CoverageStatus.WEAK,
                PedagogicalMove.COMPLETE_WITH_INSUFFICIENT_EVIDENCE,
                false,
                List.of(),
                List.of("syntax"),
                true,
                "",
                "La evidencia fue insuficiente.");
    }

    private static List<TrainingAssignmentEvaluationService.EvaluationExchange> transcriptWithTutorFalsePremise() {
        return List.of(new TrainingAssignmentEvaluationService.EvaluationExchange(
                "Observa esta variante:\n\n```c\nfor (int i = 0; i < 3; i++)\n    printf(\"%d\", i);\n```\n\n¿Dónde está el problema?",
                "No tiene error; compila y printf se ejecuta tres veces."));
    }

    private static List<TrainingAssignmentEvaluationService.EvaluationExchange> transcriptWithCorrectionAndFrustration() {
        return List.of(new TrainingAssignmentEvaluationService.EvaluationExchange(
                "Observa esta variante:\n\n```c\nfor (int i = 0; i < 3; i++)\n    printf(\"%d\", i);\n```\n\n¿Qué token lo rompe?",
                "No tiene error, compila bien, pero esta discusión ya me frustró bastante."));
    }

    private static List<TrainingAssignmentEvaluationService.EvaluationExchange> transcriptWithSharedCorrectionPhrase() {
        return List.of(new TrainingAssignmentEvaluationService.EvaluationExchange(
                "Observa esta variante:\n\n```c\nfor (int i = 0; i < 3; i++)\n    printf(\"%d\", i);\n```\n\n¿Qué error de sintaxis ves?",
                "El código compila; no necesita llaves si solo hay una instrucción."));
    }
}
