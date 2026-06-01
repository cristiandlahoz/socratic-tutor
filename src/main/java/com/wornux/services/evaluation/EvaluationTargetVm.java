package com.wornux.services.evaluation;

import java.util.UUID;

public record EvaluationTargetVm(UUID evaluationId, String subjectSlug, String evaluationSlug) {}
