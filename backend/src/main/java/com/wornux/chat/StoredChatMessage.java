package com.wornux.chat;

import org.springframework.ai.chat.messages.MessageType;

import java.time.Instant;

public record StoredChatMessage(MessageType role, String content, Instant createdAt) {
}
