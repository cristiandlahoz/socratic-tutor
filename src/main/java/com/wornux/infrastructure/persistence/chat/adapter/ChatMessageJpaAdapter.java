package com.wornux.infrastructure.persistence.chat.adapter;

import com.wornux.application.chat.port.ChatMessagePersistencePort;
import com.wornux.domain.chat.ChatMessageEntity;
import com.wornux.infrastructure.persistence.chat.ChatMessageJpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageJpaAdapter implements ChatMessagePersistencePort {

  private final ChatMessageJpaRepository chatMessageJpaRepository;

  public ChatMessageJpaAdapter(ChatMessageJpaRepository chatMessageJpaRepository) {
    this.chatMessageJpaRepository = chatMessageJpaRepository;
  }

  @Override
  public List<ChatMessageEntity> findByTranscriptIdOrderByIdAsc(UUID transcriptId) {
    return chatMessageJpaRepository.findByTranscript_IdOrderByIdAsc(transcriptId);
  }

  @Override
  public List<ChatMessageEntity> findDisplayMessages(UUID chatId, UUID clientId) {
    return chatMessageJpaRepository.findDisplayMessages(chatId, clientId);
  }
}
