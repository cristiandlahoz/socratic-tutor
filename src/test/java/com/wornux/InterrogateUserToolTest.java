package com.wornux;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.wornux.ai.tools.InterrogateUserTool;
import com.wornux.dtos.chat.questions.StudentQuestion;
import com.wornux.dtos.chat.questions.StudentQuestionAnswer;
import com.wornux.dtos.chat.questions.StudentQuestionOption;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;

class InterrogateUserToolTest {

    @Test
    @Timeout(10)
    void toolCallingManagerConvertsModelToolCallToStudentQuestionSet() {
        var expectedQuestionSet = expectedQuestionSet();
        var capturedQuestionSet = new AtomicReference<StudentQuestionSet>();
        var options = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(new InterrogateUserTool(questionHandler(capturedQuestionSet))))
                .build();
        var prompt = new Prompt("""
                necesito ayuda para resolver un ejercicio de cajero
                """, options);

        var toolExecutionResult = ToolCallingManager.builder()
                .build()
                .executeToolCalls(prompt, toolCallResponse(expectedQuestionSetJson()));

        assertThat(capturedQuestionSet.get()).isEqualTo(expectedQuestionSet);
        assertQuestionSetSchema(capturedQuestionSet.get());
        assertThat(toolExecutionResult.conversationHistory()).extracting(Message::getMessageType)
                .containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL);

        var toolResponseMessage = toolExecutionResult.conversationHistory().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(toolResponseMessage.getResponses()).singleElement().satisfies(toolResponse -> {
            assertThat(toolResponse.name()).isEqualTo(InterrogateUserTool.INTERROGATE_USER);
            assertThat(toolResponse.responseData()).contains("schema accepted");
        });

        var followUpPrompt = new Prompt(toolExecutionResult.conversationHistory(), options);
        assertThat(followUpPrompt.getInstructions()).extracting(Message::getMessageType)
                .containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL);
    }

    private InterrogateUserTool.QuestionHandler questionHandler(
            AtomicReference<StudentQuestionSet> capturedQuestionSet) {
        return questionSet -> {
            capturedQuestionSet.set(questionSet);
            var question = questionSet.questions().get(0);
            var selectedLabel = question.options().get(0).label();
            return new StudentQuestionResponse(
                    List.of(new StudentQuestionAnswer("q0", List.of(selectedLabel), "schema accepted")));
        };
    }

    private void assertQuestionSetSchema(StudentQuestionSet questionSet) {
        assertThat(questionSet).isNotNull();
        assertThat(questionSet.questions()).hasSizeBetween(1, 3).allSatisfy(this::assertQuestionSchema);
    }

    private void assertQuestionSchema(StudentQuestion question) {
        assertThat(question.question()).isNotBlank().endsWith("?");
        assertThat(question.options()).isNotEmpty();
        assertThat(question.options()).allSatisfy(option -> {
            assertThat(option.label()).isNotBlank();
            assertThat(option.description()).isNotBlank();
        });
    }

    private StudentQuestionSet expectedQuestionSet() {
        return new StudentQuestionSet(List.of(new StudentQuestion(
                "¿Qué parte del ejercicio de cajero te está frenando ahora?",
                List.of(
                        new StudentQuestionOption(
                                "No entiendo el enunciado",
                                "Todavía no tengo claro qué me pide exactamente el ejercicio."),
                        new StudentQuestionOption(
                                "Me trabé en la lógica",
                                "Entiendo el objetivo general, pero no sé cómo organizar los pasos.")))));
    }

    private String expectedQuestionSetJson() {
        return """
                {"questionSet":{"questions":[{"question":"¿Qué parte del ejercicio de cajero te está frenando ahora?","options":[{"label":"No entiendo el enunciado","description":"Todavía no tengo claro qué me pide exactamente el ejercicio."},{"label":"Me trabé en la lógica","description":"Entiendo el objetivo general, pero no sé cómo organizar los pasos."}]}]}}
                """;
    }

    private ChatResponse toolCallResponse(String arguments) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        InterrogateUserTool.INTERROGATE_USER,
                        arguments)))
                .build())));
    }
}
