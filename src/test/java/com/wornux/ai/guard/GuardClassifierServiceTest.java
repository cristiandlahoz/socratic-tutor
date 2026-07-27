package com.wornux.ai.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.wornux.ai.prompt.PromptResources;
import com.wornux.config.ApplicationProperties;
import com.wornux.data.enums.GuardAction;
import com.wornux.data.enums.GuardDecision;
import com.wornux.dtos.chat.questions.StudentQuestion;
import com.wornux.dtos.chat.questions.StudentQuestionAnswer;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.jdbc.core.simple.JdbcClient;

class GuardClassifierServiceTest {

    @Test
    void interactiveResponseUsesOneCallWithSubjectAndRealConversationRoles() {
        var prompts = new ArrayList<Prompt>();
        ChatModel model = prompt -> {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("""
                    {"decision":"SAFE","action":"ALLOW","safeUserMessage":"","directResponse":""}
                    """))));
        };
        var promptResources = mock(PromptResources.class);
        when(promptResources.guardClassifier()).thenReturn("Guard instructions\n$subjectContext$");
        var properties = new ApplicationProperties.Ai.SwitzerlandKnife();
        properties.setModel("guard-model");
        var service = new GuardClassifierService(model, promptResources, mock(JdbcClient.class), properties);

        var result = service.classifyInteractiveResponse(
            "Ayúdame a entender el bucle.",
            new StudentQuestionSet(List.of(new StudentQuestion("¿Cuánto vale x?", List.of()))),
            new StudentQuestionResponse(List.of(new StudentQuestionAnswer("q0", List.of(), "3"))),
            "<active_subject_context>Programming</active_subject_context>");

        assertThat(result.decision()).isEqualTo(GuardDecision.SAFE);
        assertThat(result.action()).isEqualTo(GuardAction.ALLOW);
        assertThat(prompts).singleElement().satisfies(prompt -> {
            assertThat(prompt.getInstructions())
                    .hasSize(4)
                    .element(0)
                    .isInstanceOf(SystemMessage.class)
                    .extracting(message -> ((SystemMessage) message).getText())
                    .asString()
                    .contains("Programming");
            assertThat(prompt.getInstructions().get(1)).isInstanceOf(UserMessage.class);
            assertThat(prompt.getInstructions().get(2)).isInstanceOf(AssistantMessage.class);
            assertThat(prompt.getInstructions().get(3)).isInstanceOf(UserMessage.class);
            assertThat(prompt.getInstructions().get(2).getText()).contains("q0: ¿Cuánto vale x?");
            assertThat(prompt.getInstructions().get(3).getText()).contains("q0 selected=[] customText=3");
            var options = (OpenAiChatOptions) prompt.getOptions();
            assertThat(options.getModel()).isEqualTo("guard-model");
            assertThat(options.getTemperature()).isZero();
            assertThat(options.getMaxTokens()).isEqualTo(2048);
            assertThat(options.getOutputSchema()).contains("safeUserMessage", "directResponse");
        });
    }
}
