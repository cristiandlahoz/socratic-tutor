package com.wornux.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findByTranscript_IdOrderByIdAsc(UUID transcriptId);

    @Query("""
            select message
            from ChatMessageEntity message
            join message.transcript transcript
            join transcript.chat chat
            where chat.id = :chatId
              and chat.clientId = :clientId
            order by transcript.createdAt asc, message.id asc
            """)
    List<ChatMessageEntity> findDisplayMessages(@Param("chatId") UUID chatId, @Param("clientId") UUID clientId);

    void deleteByTranscript_Id(UUID transcriptId);
}
