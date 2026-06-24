package com.wornux.dtos.chat;

public record ConversationTokenUsage(Integer inputTokens, Integer usagePercent) {

    public static ConversationTokenUsage empty() {
        return new ConversationTokenUsage(null, null);
    }
}
