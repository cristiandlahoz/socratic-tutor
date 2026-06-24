package com.wornux.dtos.chat;

public record ChatCompactionStatus(boolean compacted, Integer generation, Long compactedFromConversationStateId) {

    public static ChatCompactionStatus none() {
        return new ChatCompactionStatus(false, null, null);
    }
}
