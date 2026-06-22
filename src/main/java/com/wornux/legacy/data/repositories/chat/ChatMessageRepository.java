package com.wornux.legacy.data.repositories.chat;

import java.util.List;
import java.util.UUID;

import com.wornux.legacy.data.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByTranscript_IdOrderByIdAsc(Long transcriptId);

    @Query("""
           select message
           from ChatMessage message
           join message.transcript transcript
           join transcript.chat chat
           where chat.id = :chatId
             and chat.clientId = :clientId
           order by transcript.createdAt asc, message.id asc
           """)
    List<ChatMessage> findDisplayMessages(@Param("chatId") UUID chatId, @Param("clientId") UUID clientId);
}
