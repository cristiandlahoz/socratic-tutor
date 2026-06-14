package com.wornux.data.repositories.evaluation;

import java.util.List;

import com.wornux.data.entities.EvaluationAttemptQuestion;
import com.wornux.data.entities.EvaluationAttemptResponse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationAttemptResponseRepository extends JpaRepository<EvaluationAttemptResponse, Long> {

    List<EvaluationAttemptResponse> findByAttemptQuestionInOrderByAnsweredAtDesc(
            List<EvaluationAttemptQuestion> attemptQuestions);
}
