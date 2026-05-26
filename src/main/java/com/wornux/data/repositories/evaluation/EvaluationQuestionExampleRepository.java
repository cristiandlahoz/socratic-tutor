package com.wornux.data.repositories.evaluation;

import com.wornux.data.entities.EvaluationQuestionExample;
import com.wornux.data.entities.EvaluationRevision;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationQuestionExampleRepository
    extends JpaRepository<EvaluationQuestionExample, UUID> {

  List<EvaluationQuestionExample> findByEvaluationRevisionOrderByOrdinalAsc(
      EvaluationRevision evaluationRevision);
}
