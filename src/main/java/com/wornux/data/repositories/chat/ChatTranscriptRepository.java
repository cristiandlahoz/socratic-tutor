package com.wornux.data.repositories.chat;

import java.util.UUID;

import com.wornux.data.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatTranscriptRepository extends JpaRepository<ChatTranscript, Long> {

    void deleteByChat_Id(UUID chatId);
}
