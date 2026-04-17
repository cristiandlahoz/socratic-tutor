package com.wornux.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "chat_transcript")
public class ChatTranscriptEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private ChatEntity chat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "memory", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> memory = new LinkedHashMap<>();

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ChatTranscriptEntity create(ChatEntity chat) {
        var entity = new ChatTranscriptEntity();
        entity.id = UUID.randomUUID();
        entity.chat = chat;
        entity.memory = defaultMemory();
        entity.createdAt = Instant.now();
        return entity;
    }

    public String memoryText() {
        Object rawText = memory == null ? null : memory.get("text");
        return rawText == null ? "" : rawText.toString();
    }

    public void setMemoryText(String text) {
        this.memory = new LinkedHashMap<>(memory == null ? defaultMemory() : memory);
        this.memory.put("text", text == null ? "" : text);
    }

    private static Map<String, Object> defaultMemory() {
        return new LinkedHashMap<>(Map.of("text", ""));
    }
}
