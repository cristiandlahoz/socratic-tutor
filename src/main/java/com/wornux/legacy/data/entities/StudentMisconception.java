package com.wornux.legacy.data.entities;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.enums.MisconceptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "student_misconception")
@Getter
@Setter
public class StudentMisconception {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "topic_key", nullable = false, length = 32)
    private String topicKey;

    @Column(name = "misconception_key", nullable = false, length = 64)
    private String misconceptionKey;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MisconceptionStatus status;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected StudentMisconception() {}

    public static StudentMisconception create(
            UUID clientId,
            String topicKey,
            String misconceptionKey,
            String description) {
        var entity = new StudentMisconception();
        entity.clientId = clientId;
        entity.topicKey = topicKey == null || topicKey.isBlank() ? "unknown" : topicKey;
        entity.misconceptionKey = misconceptionKey;
        entity.description = description;
        entity.status = MisconceptionStatus.ACTIVE;
        entity.lastSeenAt = Instant.now();
        return entity;
    }

    public void refresh() {
        this.status = MisconceptionStatus.ACTIVE;
        this.lastSeenAt = Instant.now();
    }

    public void resolve() {
        this.status = MisconceptionStatus.RESOLVED;
    }
}
