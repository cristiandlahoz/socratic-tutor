package com.wornux.data.repositories.conversation;

import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.conversation.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    List<Conversation> findByGroupClassMember_IdOrderByUpdatedAtDesc(UUID groupClassMemberId);

    java.util.Optional<Conversation> findByIdAndGroupClassMember_Id(UUID conversationId, UUID groupClassMemberId);
}
