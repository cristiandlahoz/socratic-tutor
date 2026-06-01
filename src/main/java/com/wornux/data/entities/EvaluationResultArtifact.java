package com.wornux.data.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evaluation_result_artifact")
@Getter
@Setter
public class EvaluationResultArtifact {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "evaluation_id", nullable = false)
  private Evaluation evaluation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "revision_id", nullable = false)
  private EvaluationRevision revision;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "attempt_id", nullable = false)
  private EvaluationAttempt attempt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_payload", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> resultPayload = new LinkedHashMap<>();

  @Column(name = "completed_at", nullable = false)
  private Instant completedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected EvaluationResultArtifact() {}

  public static EvaluationResultArtifact create(
      Evaluation evaluation,
      EvaluationRevision revision,
      EvaluationAttempt attempt,
      Map<String, Object> resultPayload,
      Instant completedAt) {
    var entity = new EvaluationResultArtifact();
    entity.id = UUID.randomUUID();
    entity.evaluation = evaluation;
    entity.revision = revision;
    entity.attempt = attempt;
    entity.resultPayload =
        resultPayload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(resultPayload);
    entity.completedAt = completedAt == null ? Instant.now() : completedAt;
    entity.createdAt = Instant.now();
    return entity;
  }
}
