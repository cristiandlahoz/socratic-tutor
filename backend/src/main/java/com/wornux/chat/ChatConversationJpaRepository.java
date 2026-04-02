package com.wornux.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatConversationJpaRepository extends JpaRepository<ChatConversationEntity, UUID> {

    List<ChatConversationEntity> findByClientIdOrderByUpdatedAtDescCreatedAtDesc(UUID clientId);

    Optional<ChatConversationEntity> findByIdAndClientId(UUID id, UUID clientId);
}
