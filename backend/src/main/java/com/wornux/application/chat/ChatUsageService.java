package com.wornux.application.chat;

import com.wornux.ai.config.ChatProperties;
import com.wornux.application.chat.port.ChatPersistencePort;
import com.wornux.domain.chat.ChatTranscriptUsage;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatUsageService {

  private final ChatPersistencePort chatPort;
  private final ChatProperties chatProperties;

  public ChatUsageService(ChatPersistencePort chatPort, ChatProperties chatProperties) {
    this.chatPort = chatPort;
    this.chatProperties = chatProperties;
  }

  @Transactional
  public void updateActiveTranscriptInputTokens(UUID chatId, Integer inputTokens) {
    if (inputTokens == null) {
      return;
    }
    var chat = chatPort.findById(chatId).orElseThrow(() -> new IllegalStateException("Chat not found: " + chatId));
    var transcript = chat.getCurrentTranscript();
    if (transcript == null) {
      throw new IllegalStateException("Active transcript not found for chat: " + chatId);
    }
    transcript.setInputTokens(inputTokens);
    chat.touch();
    chatPort.save(chat);
  }

  @Transactional(readOnly = true)
  public ChatTranscriptUsage getActiveTranscriptUsage(UUID clientId, UUID chatId) {
    var chat = chatPort.findByIdAndClientId(chatId, clientId).orElse(null);
    if (chat == null || chat.getCurrentTranscript() == null || chat.getCurrentTranscript().getInputTokens() == null) {
      return ChatTranscriptUsage.empty();
    }
    var inputTokens = chat.getCurrentTranscript().getInputTokens();
    return new ChatTranscriptUsage(inputTokens, usagePercent(inputTokens));
  }

  @Transactional(readOnly = true)
  public boolean exceedsCompactionThreshold(UUID chatId) {
    var chat = chatPort.findById(chatId).orElse(null);
    if (chat == null || chat.getCurrentTranscript() == null || chat.getCurrentTranscript().getInputTokens() == null) {
      return false;
    }
    return exceedsCompactionThreshold(chat.getCurrentTranscript().getInputTokens());
  }

  boolean exceedsCompactionThreshold(int inputTokens) {
    return inputTokens > thresholdTokens();
  }

  int thresholdTokens() {
    int threshold =
        (int)
            Math.floor(
                chatProperties.getContextWindowTokens() * chatProperties.getCompactionThresholdRatio());
    if (threshold <= 0) {
      throw new IllegalStateException("Chat compaction threshold must be greater than zero");
    }
    return threshold;
  }

  int usagePercent(int inputTokens) {
    return (int) Math.round(inputTokens * 100.0 / thresholdTokens());
  }
}
