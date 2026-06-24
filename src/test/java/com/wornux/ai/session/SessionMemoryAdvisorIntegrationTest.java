package com.wornux.ai.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
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
