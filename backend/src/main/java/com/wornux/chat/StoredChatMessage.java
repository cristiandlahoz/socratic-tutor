package com.wornux.chat;

import java.time.Instant;
import org.springframework.ai.chat.messages.MessageType;

public record StoredChatMessage(MessageType role, String content, Instant createdAt) {}
