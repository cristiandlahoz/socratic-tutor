package com.wornux.services.evaluation;

import java.util.List;
import java.util.Map;

public record CurrentModeTurnContext(
    String mode,
    int answeredCount,
    int nextOrdinal,
    List<Map<String, Object>> priorQaEvidence,
    String completionIntent,
    int maxQuestions) {}
