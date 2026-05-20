package com.wornux.application.evaluation;

import java.math.BigDecimal;
import java.util.List;

public record EvaluationGradeResult(
    BigDecimal overallScore,
    String summary,
    List<String> strengths,
    List<String> weakConcepts,
    List<String> activeMisconceptions,
    List<String> tutorRecommendations,
    List<String> uncertaintyNotes) {

  public EvaluationGradeResult {
    strengths = strengths == null ? List.of() : List.copyOf(strengths);
    weakConcepts = weakConcepts == null ? List.of() : List.copyOf(weakConcepts);
    activeMisconceptions =
        activeMisconceptions == null ? List.of() : List.copyOf(activeMisconceptions);
    tutorRecommendations =
        tutorRecommendations == null ? List.of() : List.copyOf(tutorRecommendations);
    uncertaintyNotes = uncertaintyNotes == null ? List.of() : List.copyOf(uncertaintyNotes);
  }
}
