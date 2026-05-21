package com.wornux.infrastructure.persistence.chat;

import com.wornux.domain.chat.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatTranscriptJpaRepository extends JpaRepository<ChatTranscriptEntity, UUID> {

  void deleteByChat_Id(UUID chatId);
}
