package com.wornux.infrastructure.persistence.evaluation;

import com.wornux.domain.evaluation.EvaluationQuestionExampleEntity;
import com.wornux.domain.evaluation.EvaluationRevisionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationQuestionExampleJpaRepository
    extends JpaRepository<EvaluationQuestionExampleEntity, UUID> {

  List<EvaluationQuestionExampleEntity> findByEvaluationRevisionOrderByOrdinalAsc(
      EvaluationRevisionEntity evaluationRevision);
}
