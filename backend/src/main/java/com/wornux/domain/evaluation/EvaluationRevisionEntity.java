package com.wornux.domain.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import com.wornux.domain.subject.SubjectConfigRevisionEntity;
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
    name = "EvaluationRevision.withExamples",
    attributeNodes = @NamedAttributeNode("questionExamples"))
@Table(name = "evaluation_revision")
@Getter
@Setter
public class EvaluationRevisionEntity {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "evaluation_id", nullable = false)
  private EvaluationEntity evaluation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subject_config_revision_id", nullable = false)
  private SubjectConfigRevisionEntity subjectConfigRevision;

  @Column(nullable = false)
  private long version;

  @Column(nullable = false, columnDefinition = "text")
  private String instructions;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> settings = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> rubric = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "evaluationRevision", fetch = FetchType.LAZY)
  @OrderBy("ordinal asc")
  @BatchSize(size = 50)
  private List<EvaluationQuestionExampleEntity> questionExamples = new ArrayList<>();

  protected EvaluationRevisionEntity() {}

  public static EvaluationRevisionEntity create(
      EvaluationEntity evaluation,
      SubjectConfigRevisionEntity subjectConfigRevision,
      long version,
      String instructions,
      Map<String, Object> settings,
      Map<String, Object> rubric) {
    var entity = new EvaluationRevisionEntity();
    entity.id = UUID.randomUUID();
    entity.evaluation = evaluation;
    entity.subjectConfigRevision = subjectConfigRevision;
    entity.version = version;
    entity.instructions = instructions;
    entity.settings = settings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(settings);
    entity.rubric = rubric == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rubric);
    entity.createdAt = Instant.now();
    return entity;
  }
}
