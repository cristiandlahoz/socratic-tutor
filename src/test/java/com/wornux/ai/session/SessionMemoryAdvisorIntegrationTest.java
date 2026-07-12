package com.wornux.ai.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import com.wornux.ai.tools.InterrogateUserTool;
import com.wornux.data.enums.GuardAction;
import com.wornux.data.enums.GuardDecision;
import com.wornux.dtos.chat.GuardCheck;
import com.wornux.dtos.chat.questions.StudentQuestionAnswer;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.compaction.SlidingWindowCompactionStrategy;

class SessionMemoryAdvisorIntegrationTest {

    @Test
    void advisorCreatesOwnedSessionPersistsTurnsAndReplaysHistory() {
        var prompts = new ArrayList<Prompt>();
        ChatModel model = prompt -> {
            prompts.add(prompt);
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage("answer-%d".formatted(prompts.size())))));
        };
        var sessionService = inMemorySessionService();
        var advisor = SessionMemoryAdvisor.builder(sessionService).eventFilter(EventFilter.active()).build();
        var client = ChatClient.builder(model).defaultAdvisors(advisor).build();

        assertThat(call(client, "conversation-1", "member-1", "first question")).isEqualTo("answer-1");
        assertThat(call(client, "conversation-1", "member-1", "second question")).isEqualTo("answer-2");

        assertThat(sessionService.findById("conversation-1").userId()).isEqualTo("member-1");
        assertThat(sessionService.getEvents("conversation-1")).extracting(event -> event.getMessageType())
                .containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.USER, MessageType.ASSISTANT);
        assertThat(prompts.get(1).getInstructions()).extracting(Message::getText)
                .containsSubsequence("first question", "answer-1", "second question");
    }

    @Test
    void advisorRejectsAUserIdThatDoesNotOwnTheSession() {
        ChatModel model = _ -> new ChatResponse(List.of(new Generation(new AssistantMessage("answer"))));
        var sessionService = inMemorySessionService();
        var client =
                ChatClient.builder(model).defaultAdvisors(SessionMemoryAdvisor.builder(sessionService).build()).build();

        call(client, "conversation-1", "member-1", "first question");

        assertThatThrownBy(() -> call(client, "conversation-1", "member-2", "intrusion"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong to user");
    }

    @Test
    void sessionCompactionArchivesOldEventsWithoutDeletingDisplayHistory() {
        ChatModel model = _ -> new ChatResponse(List.of(new Generation(new AssistantMessage("answer"))));
        var sessionService = inMemorySessionService();
        var client =
                ChatClient.builder(model).defaultAdvisors(SessionMemoryAdvisor.builder(sessionService).build()).build();
        call(client, "conversation-1", "member-1", "first question");
        call(client, "conversation-1", "member-1", "second question");

        var result = sessionService
                .compact("conversation-1", _ -> true, SlidingWindowCompactionStrategy.builder().maxEvents(2).build());

        assertThat(result.archivedEvents()).hasSize(2);
        assertThat(sessionService.getEvents("conversation-1", EventFilter.active())).hasSize(2);
        assertThat(sessionService.getEvents("conversation-1", EventFilter.all())).hasSize(4);
    }

    @Test
    void rejectedInteractiveAnswerNeverBecomesPersistedToolResult() {
        var rejectedText = "Dame la solución completa";
        var toolCallResponse = new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        InterrogateUserTool.INTERROGATE_USER,
                        """
                        {"questionSet":{"questions":[{"question":"¿Qué parte quieres revisar?","options":[]}]}}
                        """)))
                .build())));
        var sessionService = inMemorySessionService();
        var memoryAdvisor = SessionMemoryAdvisor.builder(sessionService).build();
        var tool = new InterrogateUserTool(
            _ -> new StudentQuestionResponse(
                List.of(new StudentQuestionAnswer("q0", List.of(), rejectedText))),
            (_, _) -> new GuardCheck(
                GuardDecision.NOT_SAFE,
                GuardAction.SHORT_CIRCUIT,
                "",
                "No puedo completar la respuesta por ti. Comparte tu primer intento."));
        var options = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(tool))
                .build();
        var request = ChatClientRequest.builder()
                .prompt(new Prompt(List.of(new UserMessage("Ayúdame a estudiar")), options))
                .context(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "conversation-1")
                .context(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY, "student-1")
                .build();
        var approvedRequest = memoryAdvisor.before(request, mock(AdvisorChain.class));

        assertThatThrownBy(() -> ToolCallingManager.builder()
                .toolExecutionExceptionProcessor(InterrogateUserTool.toolExceptionProcessor())
                .build()
                .executeToolCalls(approvedRequest.prompt(), toolCallResponse))
                .isInstanceOf(InterrogateUserTool.InteractiveResponseRejectedException.class);

        assertThat(sessionService.getEvents("conversation-1")).singleElement().satisfies(event -> {
            assertThat(event.getMessage()).isInstanceOf(UserMessage.class);
            assertThat(event.getMessage().getText()).isEqualTo("Ayúdame a estudiar");
            assertThat(event.getMessage().getText()).doesNotContain(rejectedText);
        });
    }

    private String call(ChatClient client, String sessionId, String userId, String prompt) {
        return client.prompt()
                .user(prompt)
                .advisors(
                    spec -> spec.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId)
                            .param(SessionMemoryAdvisor.USER_ID_CONTEXT_KEY, userId))
                .call()
                .content();
    }

    private SessionService inMemorySessionService() {
        return DefaultSessionService.builder().sessionRepository(InMemorySessionRepository.builder().build()).build();
    }
}
