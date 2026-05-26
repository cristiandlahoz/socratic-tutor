package com.wornux.data.repositories.chat;

import com.wornux.data.entities.*;
import com.wornux.data.enums.*;
import com.wornux.domain.chat.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatTranscriptRepository extends JpaRepository<ChatTranscript, UUID> {

  void deleteByChat_Id(UUID chatId);
}
