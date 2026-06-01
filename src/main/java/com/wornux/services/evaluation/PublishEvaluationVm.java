package com.wornux.services.evaluation;

import java.util.UUID;

public record PublishEvaluationVm(
    PublishLifecycleState state, UUID revisionId, UUID guideArtifactId, String errorMessage) {}
