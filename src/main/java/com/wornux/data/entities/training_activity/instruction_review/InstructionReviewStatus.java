package com.wornux.data.entities.training_activity.instruction_review;

public enum InstructionReviewStatus {
    IDLE,
    LOCAL_INVALID,
    READY_TO_SAVE,
    NEEDS_USER_FIX,
    PENDING,
    REVIEWING,
    COMPLETED,
    SKIPPED_NO_CHANGES,
    FAILED,
    UNAVAILABLE,
    STALE,
    OVERRIDDEN
}
