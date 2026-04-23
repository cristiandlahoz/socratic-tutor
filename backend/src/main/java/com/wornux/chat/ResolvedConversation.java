package com.wornux.chat;

import java.util.List;
import java.util.UUID;

public record ResolvedConversation(
    UUID activeConversationId,
    List<ConversationSummary> conversations,
    List<StoredChatMessage> messages) {}
