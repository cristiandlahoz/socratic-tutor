package com.wornux.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversationEntity conversation;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessageEntity() {
    }

    public static ChatMessageEntity from(ChatConversationEntity conversation, Message message) {
        var entity = new ChatMessageEntity();
        entity.conversation = conversation;
        entity.role = message.getMessageType().getValue();
        entity.content = message.getText() == null ? "" : message.getText();
        entity.metadata = new LinkedHashMap<>(message.getMetadata());
        entity.createdAt = Instant.now();
        return entity;
    }

    public Message toSpringAiMessage() {
        var safeMetadata = metadata == null ? Map.<String, Object>of() : Map.copyOf(metadata);
        var messageType = MessageType.valueOf(role.toUpperCase());
        return switch (messageType) {
            case USER -> UserMessage.builder()
                    .text(content)
                    .metadata(safeMetadata)
                    .build();
            case ASSISTANT -> AssistantMessage.builder()
                    .content(content)
                    .properties(safeMetadata)
                    .build();
            case SYSTEM -> SystemMessage.builder()
                    .text(content)
                    .metadata(safeMetadata)
                    .build();
            case TOOL -> throw new IllegalStateException("Tool messages are not supported yet");
        };
    }

    public StoredChatMessage toStoredChatMessage() {
        return new StoredChatMessage(MessageType.valueOf(role.toUpperCase()), content, createdAt);
    }
}
