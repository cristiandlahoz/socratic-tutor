package com.wornux.data.entities;

import com.wornux.data.enums.EvaluationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "evaluation")
public class Evaluation {

  @Id private UUID id;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String instruction;

  @Column(name = "questions_json", columnDefinition = "jsonb")
  private String questionsJson;

  @Column(name = "answers_json", columnDefinition = "jsonb")
  private String answersJson;

  @Column(name = "report_markdown", columnDefinition = "text")
  private String reportMarkdown;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EvaluationStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static Evaluation create(String title, String instruction) {
    var now = Instant.now();
    var entity = new Evaluation();
    entity.id = UUID.randomUUID();
    entity.title = title;
    entity.instruction = instruction;
    entity.status = EvaluationStatus.PENDING;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }
}
