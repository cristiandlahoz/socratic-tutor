package com.wornux.application.chat.port;

import com.wornux.domain.chat.ChatEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatPersistencePort {
  List<ChatEntity> findByClientIdOrderByUpdatedAtDescCreatedAtDesc(UUID clientId);

  Optional<ChatEntity> findByIdAndClientId(UUID chatId, UUID clientId);

  Optional<ChatEntity> findById(UUID chatId);

  ChatEntity save(ChatEntity chatEntity);
}
