package com.wornux.data.repositories.chat;

import com.wornux.data.entities.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatTranscriptRepository extends JpaRepository<ChatTranscript, Long> {

    void deleteByChat_Id(UUID chatId);
}
