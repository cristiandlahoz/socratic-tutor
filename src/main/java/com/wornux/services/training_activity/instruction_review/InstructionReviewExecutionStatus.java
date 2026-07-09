package com.wornux.services.training_activity.instruction_review;

public enum InstructionReviewExecutionStatus {
    COMPLETED,
    MODEL_OUTPUT_INVALID,
    MODEL_EMPTY_RESPONSE,
    MODEL_TIMEOUT,
    MODEL_UNAVAILABLE
}
