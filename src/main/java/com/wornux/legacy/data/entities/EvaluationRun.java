package com.wornux.legacy.data.entities;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.enums.EvaluationRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "legacy_evaluation_run")
public class EvaluationRun {

    @Id
    private UUID id;

    @Column(name = "evaluation_id", nullable = false)
    private UUID evaluationId;

    @Column(name = "student_client_id", nullable = false)
    private UUID studentClientId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questions_asked_json")
    private String questionsAskedJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers_given_json")
    private String answersGivenJson;

    @Column(name = "report_markdown", columnDefinition = "text")
    private String reportMarkdown;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvaluationRunStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static EvaluationRun create(UUID evaluationId, UUID studentClientId, String questionsAskedJson) {
        var now = Instant.now();
        var entity = new EvaluationRun();
        entity.id = UUID.randomUUID();
        entity.evaluationId = evaluationId;
        entity.studentClientId = studentClientId;
        entity.questionsAskedJson = questionsAskedJson;
        entity.status = EvaluationRunStatus.IN_PROGRESS;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }
}
