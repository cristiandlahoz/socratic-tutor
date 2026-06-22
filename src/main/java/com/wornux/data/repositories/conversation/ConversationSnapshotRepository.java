package com.wornux.data.repositories.conversation;

import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.conversation.ConversationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationSnapshotRepository extends JpaRepository<ConversationSnapshot, Long> {
    Optional<ConversationSnapshot> findFirstByConversation_IdOrderBySnapshotNoDesc(UUID conversationId);
}
