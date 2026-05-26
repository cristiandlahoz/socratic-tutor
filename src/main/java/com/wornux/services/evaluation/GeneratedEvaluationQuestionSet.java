package com.wornux.services.evaluation;

import java.util.List;

public record GeneratedEvaluationQuestionSet(List<GeneratedEvaluationQuestion> questions) {

  public GeneratedEvaluationQuestionSet {
    questions = questions == null ? List.of() : List.copyOf(questions);
  }
}
