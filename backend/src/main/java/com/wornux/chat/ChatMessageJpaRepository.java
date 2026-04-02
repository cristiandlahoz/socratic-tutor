package com.wornux.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findByConversation_IdAndConversation_ClientIdOrderByIdAsc(java.util.UUID conversationId,
                                                                                       java.util.UUID clientId);

    List<ChatMessageEntity> findByConversation_Id(java.util.UUID conversationId, Pageable pageable);

    void deleteByConversation_Id(java.util.UUID conversationId);
}
