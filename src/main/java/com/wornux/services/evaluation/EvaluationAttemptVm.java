package com.wornux.services.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EvaluationAttemptVm(UUID attemptId, UUID evaluationRevisionId, String status, BigDecimal score,
        List<EvaluationQuestionVm> questions, Map<String, Object> feedback) {}
