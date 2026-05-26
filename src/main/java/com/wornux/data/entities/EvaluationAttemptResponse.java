package com.wornux.data.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evaluation_attempt_response")
@Getter
@Setter
public class EvaluationAttemptResponse {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "attempt_question_id", nullable = false)
  private EvaluationAttemptQuestion attemptQuestion;

  @Column(name = "free_text", columnDefinition = "text")
  private String freeText;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "selected_options", nullable = false, columnDefinition = "jsonb")
  private List<String> selectedOptions = List.of();

  @Column(precision = 5, scale = 2)
  private BigDecimal score;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rubric_result", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> rubricResult = new LinkedHashMap<>();

  @Column(columnDefinition = "text")
  private String feedback;

  @Column(name = "answered_at", nullable = false)
  private Instant answeredAt;

  protected EvaluationAttemptResponse() {}

  public static EvaluationAttemptResponse answer(
      EvaluationAttemptQuestion attemptQuestion, String freeText, List<String> selectedOptions) {
    var entity = new EvaluationAttemptResponse();
    entity.attemptQuestion = attemptQuestion;
    entity.freeText = freeText == null ? "" : freeText;
    entity.selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
    entity.answeredAt = Instant.now();
    return entity;
  }
}
