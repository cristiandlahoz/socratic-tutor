package com.wornux.services.training_activity.instruction_review;

public record InstructionReviewIssue(
        String id,
        InstructionReviewIssueSeverity severity,
        String category,
        String problemText,
        Integer startOffset,
        Integer endOffset,
        String message,
        String whyItMatters,
        String suggestedReplacement,
        String suggestionReason) {
}
