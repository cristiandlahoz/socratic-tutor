package com.wornux.data.entities;

import java.time.Instant;
import java.util.UUID;

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

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "chat")
public class Chat {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_transcript_id")
    private ChatTranscript currentTranscript;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Chat create(UUID clientId, String title) {
        var now = Instant.now();
        var entity = new Chat();
        entity.id = UUID.randomUUID();
        entity.clientId = clientId;
        entity.title = title;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void activateTranscript(ChatTranscript transcript) {
        this.currentTranscript = transcript;
        touch();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void rename(String title) {
        this.title = title;
        touch();
    }
}
