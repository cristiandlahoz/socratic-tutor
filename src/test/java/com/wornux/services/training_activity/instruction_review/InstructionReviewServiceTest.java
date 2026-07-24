package com.wornux.services.training_activity.instruction_review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.test.util.ReflectionTestUtils;

class InstructionReviewServiceTest {

    @Test
    void reviewRejectsGenericFalsePremiseForValidLoopBeforeModelCall() {
        var chatModel = mock(ChatModel.class);
        var service = service(chatModel);

        var result = service.review("Bucles en C", """
                Observa esta variante en C y explica tu razonamiento con evidencia observable.

                ```c
                for (int i = 0; i < 3; i++) {
                    printf("%d", i);
                }
                ```

                ¿Cuál es el error?
                """);

        assertThat(result.qualityStatus()).isEqualTo(InstructionQualityStatus.NEEDS_IMPROVEMENT);
        assertThat(result.summary()).contains("premisa incorrecta");
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.category()).isEqualTo("FALSE_PREMISE");
            assertThat(issue.message()).contains("error inexistente");
        });
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void reviewDoesNotTreatBrokenLoopAsFalsePremise() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response(
                "{\"analysisType\":\"GOOD\",\"analysis\":\"Lista para usar.\",\"suggestedReplacement\":\"\",\"startOffset\":0,\"endOffset\":0}"));
        var service = service(chatModel);

        var result = service.review("Bucles en C", """
                Observa esta variante en C y explica tu razonamiento con evidencia observable.

                ```c
                for (int i = 0; i < 3; i++) {
                    printf("%d", i)
                }
                ```

                ¿Dónde está el error?
                """);

        assertThat(result.qualityStatus()).isEqualTo(InstructionQualityStatus.GOOD);
        assertThat(result.summary()).isEqualTo("Lista para usar.");
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void reviewRequestsSpanishVisibleCopyFromTheModel() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response(
                "{\"analysisType\":\"NEEDS_IMPROVEMENT\",\"analysis\":\"Falta precisar la evidencia esperada.\",\"suggestedReplacement\":\"Incluye la evidencia esperada en cada respuesta.\",\"startOffset\":0,\"endOffset\":44}"));
        var service = service(chatModel);

        service.review("Arreglos", "Formula preguntas sobre arreglos y sus operaciones.");

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions().getFirst().getText())
                .contains("Devuelve únicamente JSON válido");
        assertThat(prompt.getValue().getInstructions().getLast().getText())
                .contains("debe estar exclusivamente en español");
    }

    @Test
    void reviewUsesNineHundredTokensByDefault() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response(
                "{\"analysisType\":\"GOOD\",\"analysis\":\"Lista para usar.\",\"suggestedReplacement\":\"\",\"startOffset\":0,\"endOffset\":0}"));
        var service = service(chatModel);

        service.review("Arreglos", "Formula preguntas sobre arreglos y sus operaciones.");

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(((OpenAiChatOptions) prompt.getValue().getOptions()).getMaxTokens()).isEqualTo(900);
    }

    @Test
    void reviewUsesAnExplicitConfiguredTokenBudget() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response(
                "{\"analysisType\":\"GOOD\",\"analysis\":\"Lista para usar.\",\"suggestedReplacement\":\"\",\"startOffset\":0,\"endOffset\":0}"));
        var service = service(chatModel);
        ReflectionTestUtils.setField(service, "instructionReviewMaxTokens", 1_200);

        service.review("Arreglos", "Formula preguntas sobre arreglos y sus operaciones.");

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(((OpenAiChatOptions) prompt.getValue().getOptions()).getMaxTokens()).isEqualTo(1_200);
    }

    @Test
    void reviewRejectsNonPositiveAndTooLowConfiguredTokenBudgets() {
        var service = service(mock(ChatModel.class));

        ReflectionTestUtils.setField(service, "instructionReviewMaxTokens", 0);
        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Instruction review max-tokens must be at least 256.");

        ReflectionTestUtils.setField(service, "instructionReviewMaxTokens", 255);
        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Instruction review max-tokens must be at least 256.");
    }

    @Test
    void reviewTreatsALengthFinishedJsonResponseAsInvalidOutput() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response(
                "{\"analysisType\":\"GOOD\",\"analysis\":\"Lista para usar.\",\"suggestedReplacement\":\"\",\"startOffset\":0,\"endOffset\":0}",
                "length"));
        var service = service(chatModel);

        assertThatThrownBy(() -> service.review("Arreglos", "Formula preguntas sobre arreglos y sus operaciones."))
                .isInstanceOfSatisfying(InstructionReviewModelOutputException.class, exception -> assertThat(
                        exception.getReviewResult().executionStatus()).isEqualTo(InstructionReviewExecutionStatus.MODEL_OUTPUT_INVALID));
    }

    @Test
    void reviewRejectsTheLoggedEnglishGoodPayloadUsingTheRecoverableModelOutputContract() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response(
                "{\"analysisType\":\"GOOD\",\"analysis\":\"Clear request for a quiz on C's string.h library.\",\"suggestedReplacement\":\"N/A\",\"startOffset\":0,\"endOffset\":78}"));
        var service = service(chatModel);

        assertThatThrownBy(() -> service.review("Biblioteca string.h", "Crea un cuestionario de preguntas sobre la biblioteca string.h de C."))
                .isInstanceOfSatisfying(InstructionReviewModelOutputException.class, exception -> {
                    assertThat(exception.getReviewResult().executionStatus())
                            .isEqualTo(InstructionReviewExecutionStatus.MODEL_OUTPUT_INVALID);
                    assertThat(exception.getReviewResult().summary())
                            .isEqualTo("No pudimos completar la revisión automática de instrucciones. Intenta guardar de nuevo.");
                });
    }

    @Test
    void reviewTreatsNoApplicableReplacementAsNoOptionalGoodIssue() {
        var chatModel = mock(ChatModel.class);
        var service = service(chatModel);

        for (var sentinel : List.of("N/A", "NA", "NONE", "null", "No aplica", "no-aplicable")) {
            when(chatModel.call(any(Prompt.class))).thenReturn(response(
                    "{\"analysisType\":\"GOOD\",\"analysis\":\"Las instrucciones están listas para usar.\",\"suggestedReplacement\":\"%s\",\"startOffset\":0,\"endOffset\":0}".formatted(sentinel)));

            var result = service.review("Biblioteca string.h", "Crea un cuestionario de preguntas sobre la biblioteca string.h de C.");

            assertThat(result.qualityStatus()).isEqualTo(InstructionQualityStatus.GOOD);
            assertThat(result.issues()).isEmpty();
        }
    }

    @Test
    void reviewAcceptsShortSpanishAndTechnicalGoodOutput() {
        var chatModel = mock(ChatModel.class);
        var service = service(chatModel);

        for (var analysis : List.of("Excelente.", "Muy útil.", "API REST correcta.")) {
            when(chatModel.call(any(Prompt.class))).thenReturn(response(
                    "{\"analysisType\":\"GOOD\",\"analysis\":\"%s\",\"suggestedReplacement\":null,\"startOffset\":0,\"endOffset\":0}".formatted(analysis)));

            var result = service.review("Biblioteca string.h", "Crea un cuestionario de preguntas sobre la biblioteca string.h de C.");

            assertThat(result.qualityStatus()).isEqualTo(InstructionQualityStatus.GOOD);
            assertThat(result.issues()).isEmpty();
        }
    }

    @Test
    void reviewRejectsWhyItShowsErrorPremiseForValidLoopBeforeModelCall() {
        var chatModel = mock(ChatModel.class);
        var service = service(chatModel);

        var result = service.review("Bucles en C", """
                Observa esta variante en C y explica tu razonamiento con evidencia observable.

                ```c
                for (int i = 0; i < 3; i++)
                    printf("%d", i);
                ```

                ¿Por qué da error?
                """);

        assertThat(result.qualityStatus()).isEqualTo(InstructionQualityStatus.NEEDS_IMPROVEMENT);
        assertThat(result.summary()).contains("premisa incorrecta");
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.category()).isEqualTo("FALSE_PREMISE");
            assertThat(issue.message()).contains("error inexistente");
        });
        verify(chatModel, never()).call(any(Prompt.class));
    }

    private static InstructionReviewService service(ChatModel chatModel) {
        var service = new InstructionReviewService(chatModel);
        ReflectionTestUtils.setField(service, "modelName", "test-model");
        return service;
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatResponse response(String text, String finishReason) {
        return new ChatResponse(List.of(new Generation(
                new AssistantMessage(text), ChatGenerationMetadata.builder().finishReason(finishReason).build())));
    }
}
