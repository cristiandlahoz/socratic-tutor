package com.wornux.legacy.data.repositories.chat;

import java.util.UUID;

import com.wornux.legacy.data.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatTranscriptRepository extends JpaRepository<ChatTranscript, Long> {

    void deleteByChat_Id(UUID chatId);
}
