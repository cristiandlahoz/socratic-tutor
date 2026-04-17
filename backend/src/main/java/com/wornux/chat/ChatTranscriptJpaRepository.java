package com.wornux.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatTranscriptJpaRepository extends JpaRepository<ChatTranscriptEntity, UUID> {

    List<ChatTranscriptEntity> findByChat_IdOrderByCreatedAtAsc(UUID chatId);

    void deleteByChat_Id(UUID chatId);
}
