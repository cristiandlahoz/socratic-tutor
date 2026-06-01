package com.wornux.services.evaluation;

import java.util.UUID;

public record CurrentModeTurnVm(
    UUID attemptQuestionId,
    String activeQuestion,
    AnswerConstraints answerConstraints,
    String continuationHint) {

  public record AnswerConstraints(int minChars) {}
}
