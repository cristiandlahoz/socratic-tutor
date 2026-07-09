package com.wornux.ui.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;

class MessageItemTest {

    @Test
    void conversationMessageKeepsDebuggableCodeBlocksSeparateFromSteeredFlag() {
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");

        var assistantMessage = MessageItem.conversationMessage(new MessageState(
                MessageType.ASSISTANT,
                "assistant message",
                createdAt,
                true,
                false),
                "Tutor");
        var userMessage = MessageItem.conversationMessage(new MessageState(
                MessageType.USER,
                "user message",
                createdAt,
                false,
                true),
                "Student");

        assertThat(assistantMessage.getVariant()).isEqualTo("assistant");
        assertThat(assistantMessage.isLoading()).isTrue();
        assertThat(assistantMessage.isDebuggableCodeBlocks()).isTrue();
        assertThat(assistantMessage.isSteered()).isFalse();

        assertThat(userMessage.getVariant()).isEqualTo("user");
        assertThat(userMessage.isLoading()).isFalse();
        assertThat(userMessage.isDebuggableCodeBlocks()).isFalse();
        assertThat(userMessage.isSteered()).isTrue();
    }
}
