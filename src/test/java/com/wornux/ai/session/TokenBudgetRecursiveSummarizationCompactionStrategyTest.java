package com.wornux.ai.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.compaction.CompactionRequest;
import org.springframework.ai.tokenizer.TokenCountEstimator;

class TokenBudgetRecursiveSummarizationCompactionStrategyTest {

    @Test
    void keepsRecentHistoryByTokenBudgetAndSummarizesOlderTurns() {
        var summarizationPrompt = new AtomicReference<Prompt>();
        ChatModel model = prompt -> {
            summarizationPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("summary"))));
        };
        var strategy = TokenBudgetRecursiveSummarizationCompactionStrategy.builder(ChatClient.builder(model).build())
                .recentHistoryTokenBudget(30)
                .overlapEvents(1)
                .tokenCountEstimator(new CharacterTokenCountEstimator())
                .build();
        var events = List.of(
            event(new UserMessage("archived turn student question with enough length")),
            event(new AssistantMessage("archived turn tutor answer with enough length")),
            event(new UserMessage("new q")),
            event(new AssistantMessage("new a")));

        var result = strategy
                .compact(CompactionRequest.of(Session.builder().id("conversation-1").userId("user-1").build(), events));

        assertThat(result.archivedEvents()).containsExactly(events.get(0), events.get(1));
        assertThat(result.compactedEvents()).hasSize(4);
        assertThat(result.compactedEvents().get(0).isSynthetic()).isTrue();
        assertThat(result.compactedEvents().get(1).isSynthetic()).isTrue();
        assertThat(result.compactedEvents().get(1).getMessage().getText()).isEqualTo("summary");
        assertThat(result.compactedEvents()).containsSubsequence(events.get(2), events.get(3));

        var promptText = summarizationPrompt.get().getInstructions().getLast().getText();
        assertThat(promptText).contains(
            "<conversation-to-summarize>",
            "archived turn student question",
            "archived turn tutor answer",
            "<upcoming-context purpose=\"continuity-only\">",
            "new q");
    }

    private SessionEvent event(org.springframework.ai.chat.messages.Message message) {
        return SessionEvent.builder().sessionId("conversation-1").timestamp(Instant.now()).message(message).build();
    }

    private static final class CharacterTokenCountEstimator implements TokenCountEstimator {

        @Override
        public int estimate(String text) {
            return text == null ? 0 : text.length();
        }

        @Override
        public int estimate(MediaContent mediaContent) {
            return 0;
        }

        @Override
        public int estimate(Iterable<MediaContent> mediaContents) {
            return 0;
        }
    }
}
