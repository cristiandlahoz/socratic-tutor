package com.wornux.services.evaluation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EvaluationResultArtifactVm(
    UUID resultArtifactId,
    UUID evaluationId,
    UUID revisionId,
    UUID attemptId,
    Instant completedAt,
    Map<String, Object> payload) {}
