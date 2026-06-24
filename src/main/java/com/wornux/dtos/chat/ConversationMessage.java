package com.wornux.dtos.chat;

import java.time.Instant;

import org.springframework.ai.chat.messages.MessageType;

public record ConversationMessage(MessageType role, String content, Instant createdAt) {}
