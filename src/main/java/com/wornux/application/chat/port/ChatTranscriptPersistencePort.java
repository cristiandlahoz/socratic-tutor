package com.wornux.application.chat.port;

import com.wornux.domain.chat.ChatTranscriptEntity;

public interface ChatTranscriptPersistencePort {
  ChatTranscriptEntity save(ChatTranscriptEntity transcriptEntity);
}
