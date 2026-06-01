package com.wornux.services.evaluation;

public class CurrentModeTurnFactory {

  private static final int MIN_CHARS = 10;

  public CurrentModeTurnVm fromQuestion(EvaluationQuestionVm question, String continuationHint) {
    if (question == null) {
      return null;
    }
    return new CurrentModeTurnVm(
        question.attemptQuestionId(),
        question.prompt(),
        new CurrentModeTurnVm.AnswerConstraints(MIN_CHARS),
        continuationHint);
  }
}
