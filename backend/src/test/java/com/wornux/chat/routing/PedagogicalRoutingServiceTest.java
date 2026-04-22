package com.wornux.chat.routing;

import com.wornux.chat.TutorAiProperties;
import com.wornux.chat.prompt.TutorPromptResources;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PedagogicalRoutingServiceTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final TutorAiProperties tutorAiProperties = properties();
    private final TutorPromptResources promptResources = new TutorPromptResources(new DefaultResourceLoader());
    private final PedagogicalRoutingService service = new PedagogicalRoutingService(chatModel, promptResources, tutorAiProperties);

    @Test
    void routes_quick_recall_to_direct_reference_without_model_call() {
        var mode = service.classify("No recuerdo como hacer un for, dame un ejemplo.");

        assertThat(mode).isEqualTo(PedagogicalRoutingMode.DIRECT_REFERENCE);
    }

    @Test
    void routes_code_attempt_to_debug_mode_without_model_call() {
        var mode = service.classify("""
                Llevo esto:
                ```c
                for (int i = 0; i < 10; i++) {
                    printf("%d\\n", i);
                }
                ```
                """);

        assertThat(mode).isEqualTo(PedagogicalRoutingMode.DEBUG_MY_ATTEMPT);
    }

    @Test
    void falls_back_to_model_for_ambiguous_requests() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(java.util.List.of(
                new Generation(AssistantMessage.builder()
                        .content("{\"mode\":\"CONCEPT_EXPLANATION\"}")
                        .build())
        )));

        var mode = service.classify("Ayudame por favor.");

        assertThat(mode).isEqualTo(PedagogicalRoutingMode.CONCEPT_EXPLANATION);
        verify(chatModel).call(any(Prompt.class));
    }

    private static TutorAiProperties properties() {
        var properties = new TutorAiProperties();
        properties.setRoutingModel("qwen3:4b-instruct");
        return properties;
    }
}
