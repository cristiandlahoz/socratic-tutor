package com.wornux.dtos.chat;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummary(UUID id, String title, Instant updatedAt) {}
