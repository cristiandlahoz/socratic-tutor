package com.wornux.data.repositories.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    List<Chat> findByClientIdOrderByUpdatedAtDescCreatedAtDesc(UUID clientId);

    Optional<Chat> findByIdAndClientId(UUID id, UUID clientId);
}
