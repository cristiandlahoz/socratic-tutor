package com.wornux.data.repositories.chat;

import com.wornux.data.entities.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

  List<Chat> findByClientIdOrderByUpdatedAtDescCreatedAtDesc(UUID clientId);

  Optional<Chat> findByIdAndClientId(UUID id, UUID clientId);
}
