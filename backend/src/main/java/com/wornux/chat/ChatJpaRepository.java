package com.wornux.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatJpaRepository extends JpaRepository<ChatEntity, UUID> {

  List<ChatEntity> findByClientIdOrderByUpdatedAtDescCreatedAtDesc(UUID clientId);

  Optional<ChatEntity> findByIdAndClientId(UUID id, UUID clientId);
}
