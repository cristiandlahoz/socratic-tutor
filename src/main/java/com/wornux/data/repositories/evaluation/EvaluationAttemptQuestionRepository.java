package com.wornux.data.repositories.evaluation;

import com.wornux.data.entities.EvaluationAttempt;
import com.wornux.data.entities.EvaluationAttemptQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationAttemptQuestionRepository
    extends JpaRepository<EvaluationAttemptQuestion, UUID> {

  List<EvaluationAttemptQuestion> findByAttemptOrderByOrdinalAsc(
      EvaluationAttempt attempt);

  @EntityGraph(attributePaths = "responses")
  List<EvaluationAttemptQuestion> findWithResponsesByAttemptOrderByOrdinalAsc(
      EvaluationAttempt attempt);
}
