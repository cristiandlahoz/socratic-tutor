package com.wornux.services.evaluation;

import java.util.UUID;

public record DiagnosticSessionVm(
    UUID attemptId,
    String status,
    int answeredCount,
    int maxQuestions,
    CurrentModeTurnVm currentTurn,
    String completionReason,
    DiagnosticContinuationDecision continuationDecision) {}
