package com.wornux.legacy.data.entities;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "legacy_subject_config_revision")
@Getter
@Setter
public class SubjectConfigRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private LegacySubject legacySubject;

    @Column(nullable = false)
    private long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rubric_defaults", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rubricDefaults = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_policy", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> questionPolicy = new LinkedHashMap<>();

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SubjectConfigRevision() {}

    public static SubjectConfigRevision create(
            LegacySubject legacySubject,
            long version,
            Map<String, Object> config,
            Map<String, Object> rubricDefaults,
            Map<String, Object> questionPolicy,
            String createdBy) {
        var entity = new SubjectConfigRevision();
        entity.legacySubject = legacySubject;
        entity.version = version;
        entity.config = safeMap(config);
        entity.rubricDefaults = safeMap(rubricDefaults);
        entity.questionPolicy = safeMap(questionPolicy);
        entity.createdBy = createdBy == null || createdBy.isBlank() ? "session" : createdBy;
        entity.createdAt = Instant.now();
        return entity;
    }

    public long version() {
        return version;
    }

    private static Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }
}
