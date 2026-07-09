package com.wornux.services.training_activity.instruction_review;

public class InstructionQualityReviewException extends RuntimeException {

    private final InstructionReviewResult reviewResult;
    private final InstructionReviewSnapshotDto reviewSnapshot;

    public InstructionQualityReviewException(String message, InstructionReviewResult reviewResult) {
        this(message, reviewResult, null);
    }

    public InstructionQualityReviewException(
            String message,
            InstructionReviewResult reviewResult,
            InstructionReviewSnapshotDto reviewSnapshot) {
        super(message);
        this.reviewResult = reviewResult;
        this.reviewSnapshot = reviewSnapshot;
    }

    public InstructionReviewResult getReviewResult() {
        return reviewResult;
    }

    public InstructionReviewSnapshotDto getReviewSnapshot() {
        return reviewSnapshot;
    }
}
