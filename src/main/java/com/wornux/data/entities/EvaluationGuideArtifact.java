package com.wornux.data.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "evaluation_guide_artifact")
@Getter
@Setter
public class EvaluationGuideArtifact {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "evaluation_id", nullable = false)
  private Evaluation evaluation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "revision_id", nullable = false)
  private EvaluationRevision revision;

  @Column(name = "guide_content", nullable = false, columnDefinition = "text")
  private String guideContent;

  @Column(name = "published_at", nullable = false)
  private Instant publishedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected EvaluationGuideArtifact() {}

  public static EvaluationGuideArtifact create(
      Evaluation evaluation, EvaluationRevision revision, String guideContent, Instant publishedAt) {
    var entity = new EvaluationGuideArtifact();
    entity.id = UUID.randomUUID();
    entity.evaluation = evaluation;
    entity.revision = revision;
    entity.guideContent = guideContent;
    entity.publishedAt = publishedAt == null ? Instant.now() : publishedAt;
    entity.createdAt = Instant.now();
    return entity;
  }
}
