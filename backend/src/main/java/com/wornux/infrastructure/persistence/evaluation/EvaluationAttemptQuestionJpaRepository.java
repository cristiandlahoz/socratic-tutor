package com.wornux.infrastructure.persistence.evaluation;

import com.wornux.domain.evaluation.EvaluationAttemptEntity;
import com.wornux.domain.evaluation.EvaluationAttemptQuestionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationAttemptQuestionJpaRepository
    extends JpaRepository<EvaluationAttemptQuestionEntity, UUID> {

  List<EvaluationAttemptQuestionEntity> findByAttemptOrderByOrdinalAsc(
      EvaluationAttemptEntity attempt);

  @EntityGraph(attributePaths = "responses")
  List<EvaluationAttemptQuestionEntity> findWithResponsesByAttemptOrderByOrdinalAsc(
      EvaluationAttemptEntity attempt);
}
