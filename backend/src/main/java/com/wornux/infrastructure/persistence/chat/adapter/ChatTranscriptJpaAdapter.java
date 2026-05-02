package com.wornux.infrastructure.persistence.chat.adapter;

import com.wornux.application.chat.port.ChatTranscriptPersistencePort;
import com.wornux.domain.chat.ChatTranscriptEntity;
import com.wornux.infrastructure.persistence.chat.ChatTranscriptJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class ChatTranscriptJpaAdapter implements ChatTranscriptPersistencePort {

  private final ChatTranscriptJpaRepository chatTranscriptJpaRepository;

  public ChatTranscriptJpaAdapter(ChatTranscriptJpaRepository chatTranscriptJpaRepository) {
    this.chatTranscriptJpaRepository = chatTranscriptJpaRepository;
  }

  @Override
  public ChatTranscriptEntity save(ChatTranscriptEntity transcriptEntity) {
    return chatTranscriptJpaRepository.save(transcriptEntity);
  }
}
