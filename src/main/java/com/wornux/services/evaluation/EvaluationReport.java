package com.wornux.services.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EvaluationReport(
    UUID attemptId,
    String status,
    BigDecimal score,
    Instant startedAt,
    Instant gradedAt,
    Map<String, Object> feedback,
    List<EvaluationQuestionVm> questions) {}
