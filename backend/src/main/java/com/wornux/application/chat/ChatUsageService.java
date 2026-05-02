package com.wornux.application.chat;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatUsageService {

  private final ChatJpaRepository chatRepository;
  private final ChatProperties chatProperties;

  public ChatUsageService(ChatJpaRepository chatRepository, ChatProperties chatProperties) {
    this.chatRepository = chatRepository;
    this.chatProperties = chatProperties;
  }

  @Transactional
  public void updateActiveTranscriptInputTokens(UUID chatId, Integer inputTokens) {
    if (inputTokens == null) {
      return;
    }
    var chat =
        chatRepository
            .findById(chatId)
            .orElseThrow(() -> new IllegalStateException("Chat not found: " + chatId));
    var transcript = chat.getCurrentTranscript();
    if (transcript == null) {
      throw new IllegalStateException("Active transcript not found for chat: " + chatId);
    }
    transcript.setInputTokens(inputTokens);
    chat.touch();
    chatRepository.save(chat);
  }

  @Transactional(readOnly = true)
  public ChatTranscriptUsage getActiveTranscriptUsage(UUID clientId, UUID chatId) {
    var chat = chatRepository.findByIdAndClientId(chatId, clientId).orElse(null);
    if (chat == null
        || chat.getCurrentTranscript() == null
        || chat.getCurrentTranscript().getInputTokens() == null) {
      return ChatTranscriptUsage.empty();
    }
    var inputTokens = chat.getCurrentTranscript().getInputTokens();
    return new ChatTranscriptUsage(inputTokens, usagePercent(inputTokens));
  }

  @Transactional(readOnly = true)
  public boolean exceedsCompactionThreshold(UUID chatId) {
    var chat = chatRepository.findById(chatId).orElse(null);
    if (chat == null
        || chat.getCurrentTranscript() == null
        || chat.getCurrentTranscript().getInputTokens() == null) {
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
                chatProperties.getContextWindowTokens()
                    * chatProperties.getCompactionThresholdRatio());
    if (threshold <= 0) {
      throw new IllegalStateException("Chat compaction threshold must be greater than zero");
    }
    return threshold;
  }

  int usagePercent(int inputTokens) {
    return (int) Math.round(inputTokens * 100.0 / thresholdTokens());
  }
}
