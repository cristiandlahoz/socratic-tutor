package com.wornux.infrastructure.persistence.chat.adapter;

import com.wornux.application.chat.port.ChatPersistencePort;
import com.wornux.domain.chat.ChatEntity;
import com.wornux.infrastructure.persistence.chat.ChatJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatJpaAdapter implements ChatPersistencePort {

  private final ChatJpaRepository chatJpaRepository;

  public ChatJpaAdapter(ChatJpaRepository chatJpaRepository) {
    this.chatJpaRepository = chatJpaRepository;
  }

  @Override
  public List<ChatEntity> findByClientIdOrderByUpdatedAtDescCreatedAtDesc(UUID clientId) {
    return chatJpaRepository.findByClientIdOrderByUpdatedAtDescCreatedAtDesc(clientId);
  }

  @Override
  public Optional<ChatEntity> findByIdAndClientId(UUID chatId, UUID clientId) {
    return chatJpaRepository.findByIdAndClientId(chatId, clientId);
  }

  @Override
  public Optional<ChatEntity> findById(UUID chatId) {
    return chatJpaRepository.findById(chatId);
  }

  @Override
  public ChatEntity save(ChatEntity chatEntity) {
    return chatJpaRepository.save(chatEntity);
  }
}
