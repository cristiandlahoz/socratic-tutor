package com.wornux.presentation.chat;

import com.wornux.domain.chat.*;
import java.time.Instant;
import org.springframework.ai.chat.messages.MessageType;

public record MessageUiState(MessageType role, String content, Instant createdAt, boolean loading) {

    public static MessageUiState fromStored(StoredChatMessage message) {
        return new MessageUiState(message.role(), message.content(), message.createdAt(), false);
    }

    public static MessageUiState user(String content, Instant createdAt) {
        return new MessageUiState(MessageType.USER, content, createdAt, false);
    }

    public static MessageUiState assistantLoading(Instant createdAt) {
        return new MessageUiState(MessageType.ASSISTANT, "", createdAt, true);
    }

    public MessageUiState append(String token) {
        return new MessageUiState(role, content + token, createdAt, false);
    }

    public MessageUiState stopLoading() {
        return new MessageUiState(role, content, createdAt, false);
    }

    public MessageUiState fallback(String fallbackContent) {
        var nextContent = content.isBlank() ? fallbackContent : content;
        return new MessageUiState(role, nextContent, createdAt, false);
    }
}
