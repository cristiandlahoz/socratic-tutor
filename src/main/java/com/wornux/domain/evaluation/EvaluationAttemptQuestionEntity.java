package com.wornux.domain.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
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
@Table(name = "evaluation_attempt_question")
@Getter
@Setter
public class EvaluationAttemptQuestionEntity {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "attempt_id", nullable = false)
  private EvaluationAttemptEntity attempt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_example_id")
  private EvaluationQuestionExampleEntity sourceExample;

  @Column(name = "question_key", nullable = false, length = 96)
  private String questionKey;

  @Column(name = "blueprint_key", nullable = false, length = 96)
  private String blueprintKey;

  @Column(nullable = false)
  private int ordinal;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "question_snapshot", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> questionSnapshot = new LinkedHashMap<>();

  @Column(name = "question_hash", nullable = false, length = 64)
  private String questionHash;

  @OneToMany(
      mappedBy = "attemptQuestion",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @OrderBy("answeredAt desc")
  @BatchSize(size = 50)
  private List<EvaluationAttemptResponseEntity> responses = new ArrayList<>();

  protected EvaluationAttemptQuestionEntity() {}

  public static EvaluationAttemptQuestionEntity generated(
      EvaluationAttemptEntity attempt,
      EvaluationQuestionExampleEntity sourceExample,
      String questionKey,
      String blueprintKey,
      int ordinal,
      Map<String, Object> questionSnapshot,
      String questionHash) {
    var entity = new EvaluationAttemptQuestionEntity();
    entity.id = UUID.randomUUID();
    entity.attempt = attempt;
    entity.sourceExample = sourceExample;
    entity.questionKey = questionKey;
    entity.blueprintKey = blueprintKey;
    entity.ordinal = ordinal;
    entity.questionSnapshot =
        questionSnapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(questionSnapshot);
    entity.questionHash = questionHash;
    return entity;
  }
}
