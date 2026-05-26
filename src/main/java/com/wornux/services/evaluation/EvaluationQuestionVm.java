package com.wornux.services.evaluation;

import java.util.List;
import java.util.UUID;

public record EvaluationQuestionVm(
    UUID attemptQuestionId,
    String questionKey,
    String blueprintKey,
    int ordinal,
    String topicKey,
    String difficulty,
    String prompt,
    List<String> options) {}
