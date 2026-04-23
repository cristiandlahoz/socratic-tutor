package com.wornux.chat;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatTranscriptJpaRepository extends JpaRepository<ChatTranscriptEntity, UUID> {

  List<ChatTranscriptEntity> findByChat_IdOrderByCreatedAtAsc(UUID chatId);

  void deleteByChat_Id(UUID chatId);
}
