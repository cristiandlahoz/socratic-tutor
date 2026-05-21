package com.wornux.infrastructure.persistence.evaluation;

import com.wornux.domain.evaluation.EvaluationAttemptQuestionEntity;
import com.wornux.domain.evaluation.EvaluationAttemptResponseEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationAttemptResponseJpaRepository
    extends JpaRepository<EvaluationAttemptResponseEntity, Long> {

  List<EvaluationAttemptResponseEntity> findByAttemptQuestionInOrderByAnsweredAtDesc(
      List<EvaluationAttemptQuestionEntity> attemptQuestions);
}
