package com.wornux.services.training_activity.instruction_review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
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
}
