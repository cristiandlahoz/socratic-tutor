package com.wornux.services.training_activity.instruction_review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;

public record InstructionReviewSnapshotDto(
        UUID activityId,
        String reviewHash,
        InstructionReviewStatus reviewStatus,
        InstructionQualityStatus qualityStatus,
        boolean canSave,
        String message,
        boolean modelCalled,
        boolean fromCache,
        List<InstructionLintIssueDto> issues,
        String recreatedInstructions,
        Instant reviewedAt) {

    public boolean isSaveableGoodReview() {
        return canSave && qualityStatus == InstructionQualityStatus.GOOD;
    }

    public boolean requiresVisibleReviewConfirmation() {
        return reviewStatus == InstructionReviewStatus.COMPLETED_FROM_CACHE
                && qualityStatus == InstructionQualityStatus.GOOD
                && issues != null
                && !issues.isEmpty();
    }
}
