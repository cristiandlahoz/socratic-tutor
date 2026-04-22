package com.wornux.chat.routing;

import com.wornux.chat.prompt.TutorPromptResources;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PedagogicalRoutingAdvisorTest {

    private final PedagogicalRoutingService routingService = mock(PedagogicalRoutingService.class);
    private final TutorPromptResources promptResources = new TutorPromptResources(new DefaultResourceLoader());
    private final PedagogicalRoutingAdvisor advisor = new PedagogicalRoutingAdvisor(175, routingService, promptResources);

    @Test
    void direct_reference_mode_adds_targeted_examples() {
        when(routingService.classify("No recuerdo como hacer un for")).thenReturn(PedagogicalRoutingMode.DIRECT_REFERENCE);

        var patched = invoke("No recuerdo como hacer un for");

        assertThat(patched.context()).containsEntry("teaching_mode", "direct_reference");
        assertThat(lastSystemMessage(patched))
                .contains("Teaching mode: DIRECT_REFERENCE")
                .contains("Few-shot examples for direct reference")
                .contains("scanf");
    }

    @Test
    void debug_mode_reuses_exercise_examples() {
        String userText = "Aqui esta mi intento";
        when(routingService.classify(userText)).thenReturn(PedagogicalRoutingMode.DEBUG_MY_ATTEMPT);

        var patched = invoke(userText);

        assertThat(patched.context()).containsEntry("teaching_mode", "debug_my_attempt");
        assertThat(lastSystemMessage(patched))
                .contains("Teaching mode: DEBUG_MY_ATTEMPT")
                .contains("Few-shot examples for exercise coaching")
                .contains("Ya lo resolví casi completo");
    }

    @Test
    void concept_mode_adds_only_strategy_instruction() {
        String userText = "Que es un puntero?";
        when(routingService.classify(userText)).thenReturn(PedagogicalRoutingMode.CONCEPT_EXPLANATION);

        var patched = invoke(userText);

        assertThat(patched.context()).containsEntry("teaching_mode", "concept_explanation");
        assertThat(lastSystemMessage(patched))
                .contains("Teaching mode: CONCEPT_EXPLANATION")
                .doesNotContain("Few-shot examples");
    }

    private ChatClientRequest invoke(String userText) {
        final ChatClientRequest[] captured = new ChatClientRequest[1];
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return null;
        });
        advisor.adviseCall(request(userText), chain);
        return captured[0];
    }

    private static ChatClientRequest request(String userText) {
        return ChatClientRequest.builder()
                .prompt(Prompt.builder().messages(new UserMessage(userText)).build())
                .context(Map.of())
                .build();
    }

    private static String lastSystemMessage(ChatClientRequest request) {
        return request.prompt().getInstructions().stream()
                .filter(SystemMessage.class::isInstance)
                .map(message -> message.getText())
                .reduce((first, second) -> second)
                .orElse("");
    }
}
