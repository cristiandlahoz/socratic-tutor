package com.wornux.services.evaluation;

import java.time.Instant;
import java.util.UUID;

public record EvaluationGuideArtifactVm(
    UUID guideArtifactId,
    UUID evaluationId,
    UUID revisionId,
    String guideContent,
    Instant publishedAt) {}
