package com.wornux.chat.profile;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_topic_mastery")
public class StudentTopicMasteryEntity {

    @EmbeddedId
    private StudentTopicMasteryId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "mastery_level", nullable = false, length = 16)
    private MasteryLevel masteryLevel;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected StudentTopicMasteryEntity() {
    }

    public static StudentTopicMasteryEntity create(UUID clientId, TopicKey topicKey) {
        var entity = new StudentTopicMasteryEntity();
        entity.id = new StudentTopicMasteryId(clientId, topicKey);
        entity.masteryLevel = MasteryLevel.UNKNOWN;
        entity.evidenceCount = 0;
        entity.lastSeenAt = Instant.now();
        return entity;
    }

    public StudentTopicMasteryId getId() {
        return id;
    }

    public TopicKey topicKey() {
        return id.topic();
    }

    public MasteryLevel getMasteryLevel() {
        return masteryLevel;
    }

    public void setMasteryLevel(MasteryLevel masteryLevel) {
        this.masteryLevel = masteryLevel;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public void incrementEvidence() {
        this.evidenceCount++;
        this.lastSeenAt = Instant.now();
    }
}
