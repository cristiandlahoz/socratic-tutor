package com.wornux.services.evaluation;

import java.util.List;
import java.util.Map;

public interface DiagnosticModePolicy {

  DiagnosticContinuationDecision decideContinuation(
      String answer,
      int answeredCount,
      int minQuestions,
      int maxQuestions,
      List<Map<String, Object>> evidence);
}
