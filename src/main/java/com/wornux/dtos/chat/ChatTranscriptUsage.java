package com.wornux.dtos.chat;

public record ChatTranscriptUsage(Integer inputTokens, Integer usagePercent) {

  public static ChatTranscriptUsage empty() {
    return new ChatTranscriptUsage(null, null);
  }
}
