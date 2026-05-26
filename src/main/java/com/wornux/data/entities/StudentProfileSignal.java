package com.wornux.data.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "student_profile_signal")
public class StudentProfileSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "turn_id", nullable = false)
    private UUID turnId;

    @Column(name = "signal_type", nullable = false, length = 32)
    private String signalType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StudentProfileSignal() {}

    public static StudentProfileSignal from(
            UUID clientId,
            UUID conversationId,
            UUID turnId,
            String signalType,
            Map<String, Object> payload) {
        var entity = new StudentProfileSignal();
        entity.clientId = clientId;
        entity.conversationId = conversationId;
        entity.turnId = turnId;
        entity.signalType = signalType;
        entity.payload = new LinkedHashMap<>(payload);
        entity.createdAt = Instant.now();
        return entity;
    }
}
