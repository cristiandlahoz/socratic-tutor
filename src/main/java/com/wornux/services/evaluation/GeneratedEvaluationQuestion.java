package com.wornux.services.evaluation;

import java.util.List;
import java.util.Map;

public record GeneratedEvaluationQuestion(
    String questionKey,
    String blueprintKey,
    int ordinal,
    String topicKey,
    String difficulty,
    String prompt,
    List<String> options,
    Map<String, Object> expectedAnswer,
    Map<String, Object> rubric,
    List<String> sourceExampleIds) {

  public GeneratedEvaluationQuestion {
    options = options == null ? List.of() : List.copyOf(options);
    expectedAnswer = expectedAnswer == null ? Map.of() : Map.copyOf(expectedAnswer);
    rubric = rubric == null ? Map.of() : Map.copyOf(rubric);
    sourceExampleIds = sourceExampleIds == null ? List.of() : List.copyOf(sourceExampleIds);
  }
}
