package com.wornux.dtos.chat;

import java.util.UUID;

public record ChatCompactionStatus(boolean compacted, Integer level, UUID compactedFromTranscriptId) {

    public static ChatCompactionStatus none() {
        return new ChatCompactionStatus(false, null, null);
    }
}
