package com.wornux.data.entities;

import com.wornux.domain.chat.StoredChatMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transcript_id", nullable = false)
    private ChatTranscript transcript;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ChatMessage from(ChatTranscript transcript, Message message) {
        var entity = new ChatMessage();
        entity.transcript = transcript;
        entity.role = message.getMessageType().getValue();
        entity.content = message.getText() == null ? "" : message.getText();
        entity.metadata = new LinkedHashMap<>(message.getMetadata());
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            entity.metadata.put(
                "toolResponses",
                toolResponseMessage.getResponses()
                        .stream()
                        .map(
                            response -> Map.of(
                                "id",
                                response.id(),
                                "name",
                                response.name(),
                                "responseData",
                                response.responseData()))
                        .toList());
            entity.content = toolResponseMessage.getResponses()
                    .stream()
                    .map(response -> response.name() + ": " + response.responseData())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        }
        entity.createdAt = Instant.now();
        return entity;
    }

    public Message toSpringAiMessage() {
        var safeMetadata = metadata == null ? Map.<String, Object>of() : Map.copyOf(metadata);
        var messageType = MessageType.valueOf(role.toUpperCase());
        return switch (messageType) {
            case USER -> UserMessage.builder().text(content).metadata(safeMetadata).build();
            case ASSISTANT -> AssistantMessage.builder().content(content).properties(safeMetadata).build();
            case TOOL -> ToolResponseMessage.builder()
                    .responses(toToolResponses(safeMetadata))
                    .metadata(safeMetadata)
                    .build();
            case SYSTEM -> throw new IllegalStateException("System messages must not be stored in chat_message");
        };
    }

    public StoredChatMessage toStoredChatMessage() {
        return new StoredChatMessage(MessageType.valueOf(role.toUpperCase()), content, createdAt);
    }

    public boolean isToolMessage() {
        return MessageType.valueOf(role.toUpperCase()) == MessageType.TOOL;
    }

    private static List<ToolResponseMessage.ToolResponse> toToolResponses(Map<String, Object> metadata) {
        Object rawResponses = metadata.get("toolResponses");
        if (!(rawResponses instanceof List<?> responses)) {
            return List.of();
        }
        return responses.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(
                    response -> new ToolResponseMessage.ToolResponse(String.valueOf(response.get("id")),
                            String.valueOf(response.get("name")),
                            String.valueOf(response.get("responseData"))))
                .toList();
    }
}
