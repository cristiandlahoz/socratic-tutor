package com.wornux.ui.chat;

import java.time.Instant;
import org.springframework.ai.chat.messages.MessageType;

import com.wornux.dtos.chat.*;

public record MessageState(MessageType role, String content, Instant createdAt, boolean loading) {

    public static MessageState fromStored(StoredChatMessage message) {
        return new MessageState(message.role(), message.content(), message.createdAt(), false);
    }

    public static MessageState user(String content, Instant createdAt) {
        return new MessageState(MessageType.USER, content, createdAt, false);
    }

    public static MessageState assistantLoading(Instant createdAt) {
        return new MessageState(MessageType.ASSISTANT, "", createdAt, true);
    }

    public MessageState append(String token) {
        return new MessageState(role, content + token, createdAt, false);
    }

    public MessageState stopLoading() {
        return new MessageState(role, content, createdAt, false);
    }

    public MessageState fallback(String fallbackContent) {
        var nextContent = content.isBlank() ? fallbackContent : content;
        return new MessageState(role, nextContent, createdAt, false);
    }
}
