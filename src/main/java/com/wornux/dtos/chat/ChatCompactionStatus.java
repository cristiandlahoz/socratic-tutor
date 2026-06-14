package com.wornux.dtos.chat;

public record ChatCompactionStatus(boolean compacted, Integer level, Long compactedFromTranscriptId) {

    public static ChatCompactionStatus none() {
        return new ChatCompactionStatus(false, null, null);
    }
}
