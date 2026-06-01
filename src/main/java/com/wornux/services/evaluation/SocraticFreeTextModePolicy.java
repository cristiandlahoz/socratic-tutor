package com.wornux.services.evaluation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SocraticFreeTextModePolicy implements DiagnosticModePolicy {

  @Override
  public DiagnosticContinuationDecision decideContinuation(
      String answer,
      int answeredCount,
      int minQuestions,
      int maxQuestions,
      List<Map<String, Object>> evidence) {
    if (answeredCount >= maxQuestions) {
      return DiagnosticContinuationDecision.COMPLETE_MAX_QUESTIONS;
    }
    if (answeredCount < minQuestions) {
      return DiagnosticContinuationDecision.CONTINUE;
    }
    if (signalsSufficientEvidence(answer, evidence)) {
      return DiagnosticContinuationDecision.COMPLETE_MODEL_STOP;
    }
    return DiagnosticContinuationDecision.CONTINUE;
  }

  private boolean signalsSufficientEvidence(String answer, List<Map<String, Object>> evidence) {
    var normalized = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
    if (normalized.contains("suficiente")
        || normalized.contains("sufficient")
        || normalized.contains("stop")) {
      return true;
    }
    return evidence != null && evidence.size() >= 3;
  }
}
