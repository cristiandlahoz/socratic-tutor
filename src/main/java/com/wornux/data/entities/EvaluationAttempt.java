package com.wornux.data.entities;

import com.wornux.data.enums.EvaluationAttemptStatus;
import com.wornux.data.enums.EvaluationAttemptCompletionReason;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@NamedEntityGraph(
    name = "EvaluationAttempt.withQuestions",
    attributeNodes = @NamedAttributeNode("questions"))
@NamedEntityGraph(
    name = "EvaluationAttempt.report",
    attributeNodes = {@NamedAttributeNode("evaluationRevision"), @NamedAttributeNode("questions")})
@Table(name = "evaluation_attempt")
@Getter
@Setter
public class EvaluationAttempt {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "evaluation_revision_id", nullable = false)
  private EvaluationRevision evaluationRevision;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chat_id")
  private Chat chat;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private EvaluationAttemptStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "completion_reason", length = 32)
  private EvaluationAttemptCompletionReason completionReason;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "graded_at")
  private Instant gradedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "profile_snapshot", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> profileSnapshot = new LinkedHashMap<>();

  @Column(name = "profile_version", nullable = false)
  private long profileVersion;

  @Column(precision = 5, scale = 2)
  private BigDecimal score;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> feedback = new LinkedHashMap<>();

  @OneToMany(
      mappedBy = "attempt",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @OrderBy("ordinal asc")
  @BatchSize(size = 50)
  private List<EvaluationAttemptQuestion> questions = new ArrayList<>();

  @Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  protected EvaluationAttempt() {}

  public static EvaluationAttempt launch(
      EvaluationRevision evaluationRevision,
      UUID clientId,
      Chat chat,
      Map<String, Object> profileSnapshot,
      long profileVersion) {
    var entity = new EvaluationAttempt();
    entity.id = UUID.randomUUID();
    entity.evaluationRevision = evaluationRevision;
    entity.clientId = clientId;
    entity.chat = chat;
    entity.status = EvaluationAttemptStatus.READY_TO_RUN;
    entity.startedAt = Instant.now();
    entity.profileSnapshot =
        profileSnapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(profileSnapshot);
    entity.profileVersion = profileVersion;
    return entity;
  }

  public EvaluationAttemptQuestion addGeneratedQuestion(
      EvaluationQuestionExample sourceExample,
      String questionKey,
      String blueprintKey,
      int ordinal,
      Map<String, Object> questionSnapshot,
      String questionHash) {
    var attemptQuestion =
        EvaluationAttemptQuestion.generated(
            this, sourceExample, questionKey, blueprintKey, ordinal, questionSnapshot, questionHash);
    questions.add(attemptQuestion);
    return attemptQuestion;
  }

  public void markSubmitted() {
    this.status = EvaluationAttemptStatus.RUNNING;
    this.submittedAt = Instant.now();
  }

  public void applyGrade(BigDecimal score, Map<String, Object> feedback) {
    this.status = EvaluationAttemptStatus.COMPLETED;
    this.completionReason = EvaluationAttemptCompletionReason.LEGACY_GRADED;
    this.score = score;
    this.feedback = feedback == null ? new LinkedHashMap<>() : new LinkedHashMap<>(feedback);
    this.completedAt = Instant.now();
    this.gradedAt = Instant.now();
  }

  public void markCompleted(EvaluationAttemptCompletionReason reason) {
    this.status = EvaluationAttemptStatus.COMPLETED;
    this.completionReason = reason;
    this.completedAt = Instant.now();
  }
}
