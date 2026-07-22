package com.wornux.ui.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.wornux.services.chat.ModelAvailabilityStatus;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;

class ConversationStateTest {

    @Test
    void devResponseAllowsSubmittingWithoutAConnectedModel() {
        var state = new ConversationState();
        state.modelAvailabilityStatus().set(ModelAvailabilityStatus.OFFLINE);

        assertThat(state.composerSubmitAllowed().peek()).isFalse();

        state.devResponseAvailable().set(true);
        state.devResponseEnabled().set(true);

        assertThat(state.composerSubmitAllowed().peek()).isTrue();
    }

    @Test
    void applyMessagesSnapshotPreservesSignalsWhenMessagesKeepIdentity() {
        var state = new ConversationState();
        var userCreatedAt = Instant.parse("2026-07-08T18:00:00Z");
        var assistantCreatedAt = Instant.parse("2026-07-08T18:00:01Z");

        state.replaceMessages(List.of(
            new MessageState(MessageType.USER, "question", userCreatedAt, false),
            new MessageState(MessageType.ASSISTANT, "", assistantCreatedAt, true)));
        var userSignal = state.messages().peek().get(0);
        var assistantSignal = state.messages().peek().get(1);

        state.applyMessagesSnapshot(List.of(
            new MessageState(MessageType.USER, "question", userCreatedAt, false),
            new MessageState(MessageType.ASSISTANT, "answer", assistantCreatedAt, false)));

        assertThat(state.messages().peek()).containsExactly(userSignal, assistantSignal);
        assertThat(assistantSignal.peek())
                .isEqualTo(new MessageState(MessageType.ASSISTANT, "answer", assistantCreatedAt, false));
    }

    @Test
    void applyMessagesSnapshotFallsBackToReplaceWhenMessageIdentityChanges() {
        var state = new ConversationState();
        var originalCreatedAt = Instant.parse("2026-07-08T18:00:00Z");
        var replacementCreatedAt = Instant.parse("2026-07-08T18:00:01Z");

        state.replaceMessages(List.of(new MessageState(MessageType.USER, "original", originalCreatedAt, false)));
        var originalSignal = state.messages().peek().getFirst();

        state.applyMessagesSnapshot(List.of(new MessageState(MessageType.USER, "replacement", replacementCreatedAt, false)));

        assertThat(state.messages().peek()).hasSize(1);
        assertThat(state.messages().peek().getFirst()).isNotSameAs(originalSignal);
        assertThat(state.messages().peek().getFirst().peek())
                .isEqualTo(new MessageState(MessageType.USER, "replacement", replacementCreatedAt, false));
    }
}
