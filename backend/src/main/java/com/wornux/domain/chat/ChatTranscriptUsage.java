package com.wornux.domain.chat;

public record ChatTranscriptUsage(Integer inputTokens, Integer usagePercent) {

  public static ChatTranscriptUsage empty() {
    return new ChatTranscriptUsage(null, null);
  }
}
