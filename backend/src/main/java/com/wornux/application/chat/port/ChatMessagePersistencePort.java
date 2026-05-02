package com.wornux.application.chat.port;

import com.wornux.domain.chat.ChatMessageEntity;
import java.util.List;
import java.util.UUID;

public interface ChatMessagePersistencePort {
  List<ChatMessageEntity> findByTranscriptIdOrderByIdAsc(UUID transcriptId);

  List<ChatMessageEntity> findDisplayMessages(UUID chatId, UUID clientId);
}
