package com.wornux.services.training_activity.instruction_review;

public class InstructionReviewUnavailableException extends RuntimeException {

    private final InstructionReviewResult reviewResult;
    private final InstructionReviewSnapshotDto reviewSnapshot;

    public InstructionReviewUnavailableException(String message, InstructionReviewResult reviewResult, Throwable cause) {
        this(message, reviewResult, null, cause);
    }

    public InstructionReviewUnavailableException(
            String message,
            InstructionReviewResult reviewResult,
            InstructionReviewSnapshotDto reviewSnapshot,
            Throwable cause) {
        super(message, cause);
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
