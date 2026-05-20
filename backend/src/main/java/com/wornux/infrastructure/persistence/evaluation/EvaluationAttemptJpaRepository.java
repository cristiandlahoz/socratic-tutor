package com.wornux.infrastructure.persistence.evaluation;

import com.wornux.domain.evaluation.EvaluationAttemptEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvaluationAttemptJpaRepository
    extends JpaRepository<EvaluationAttemptEntity, UUID> {

  @EntityGraph(value = "EvaluationAttempt.withQuestions")
  List<EvaluationAttemptEntity>
      findByClientIdAndEvaluationRevision_Evaluation_Subject_SlugOrderByStartedAtDesc(
          UUID clientId, String subjectSlug);

  @EntityGraph(value = "EvaluationAttempt.withQuestions")
  List<EvaluationAttemptEntity> findByClientIdOrderByStartedAtDesc(UUID clientId);

  @EntityGraph(value = "EvaluationAttempt.report")
  Optional<EvaluationAttemptEntity>
      findFirstByClientIdAndEvaluationRevision_Evaluation_Subject_IdOrderByStartedAtDesc(
          UUID clientId, UUID subjectId);

  @EntityGraph(value = "EvaluationAttempt.report")
  @Query("select a from EvaluationAttemptEntity a where a.id = :id")
  Optional<EvaluationAttemptEntity> findReportById(UUID id);
}
