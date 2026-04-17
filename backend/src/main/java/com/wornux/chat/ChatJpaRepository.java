package com.wornux.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatJpaRepository extends JpaRepository<ChatEntity, UUID> {

    List<ChatEntity> findByClientIdOrderByUpdatedAtDescCreatedAtDesc(UUID clientId);

    Optional<ChatEntity> findByIdAndClientId(UUID id, UUID clientId);
}
