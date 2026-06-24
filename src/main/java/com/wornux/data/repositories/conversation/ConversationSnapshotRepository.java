package com.wornux.data.repositories.conversation;

import com.wornux.data.entities.conversation.ConversationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationSnapshotRepository extends JpaRepository<ConversationSnapshot, Long> {}
