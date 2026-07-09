package com.wornux.ui.conversation;

import java.time.Instant;

import com.wornux.dtos.chat.*;
import org.springframework.ai.chat.messages.MessageType;

public record MessageState(MessageType role, String content, Instant createdAt, boolean loading, boolean steered) {

    public MessageState(MessageType role, String content, Instant createdAt, boolean loading) {
        this(role, content, createdAt, loading, false);
    }

    public static MessageState fromConversation(ConversationMessage message) {
        return new MessageState(message.role(), message.content(), message.createdAt(), false, false);
    }

    public static MessageState user(String content, Instant createdAt) {
        return new MessageState(MessageType.USER, content, createdAt, false, false);
    }

    public static MessageState assistantLoading(Instant createdAt) {
        return new MessageState(MessageType.ASSISTANT, "", createdAt, true, false);
    }

    public MessageState append(String token) {
        return new MessageState(role, content + token, createdAt, false, steered);
    }

    public MessageState withContent(String content) {
        return new MessageState(role, content, createdAt, loading, steered);
    }

    public MessageState withSteeredContent(String content) {
        return new MessageState(role, content, createdAt, loading, true);
    }

    public MessageState stopLoading() {
        return new MessageState(role, content, createdAt, false, steered);
    }

    public MessageState fallback(String fallbackContent) {
        var nextContent = content.isBlank() ? fallbackContent : content;
        return new MessageState(role, nextContent, createdAt, false, steered);
    }
}
