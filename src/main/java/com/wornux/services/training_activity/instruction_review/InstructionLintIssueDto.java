package com.wornux.services.training_activity.instruction_review;

public record InstructionLintIssueDto(
        String issueKey,
        String code,
        String severity,
        Integer startOffset,
        Integer endOffset,
        String message,
        String whyItMatters,
        String suggestedReplacement,
        String suggestionReason) {
}
