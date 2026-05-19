package com.wornux.domain.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "student_misconception")
@Getter
@Setter
public class StudentMisconceptionEntity {

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

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MisconceptionStatus status;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected StudentMisconceptionEntity() {}

    public static StudentMisconceptionEntity create(
            UUID clientId,
            String topicKey,
            String misconceptionKey,
            String description,
            BigDecimal confidence) {
        var entity = new StudentMisconceptionEntity();
        entity.clientId = clientId;
        entity.topicKey = topicKey == null || topicKey.isBlank() ? "unknown" : topicKey;
        entity.misconceptionKey = misconceptionKey;
        entity.description = description;
        entity.confidence = confidence;
        entity.status = MisconceptionStatus.ACTIVE;
        entity.lastSeenAt = Instant.now();
        return entity;
    }

    public void refresh(BigDecimal confidence) {
        this.confidence = confidence;
        this.status = MisconceptionStatus.ACTIVE;
        this.lastSeenAt = Instant.now();
    }

    public void resolve() {
        this.status = MisconceptionStatus.RESOLVED;
    }
}
