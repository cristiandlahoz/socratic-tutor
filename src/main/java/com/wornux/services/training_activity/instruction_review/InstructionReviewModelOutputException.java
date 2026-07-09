package com.wornux.services.training_activity.instruction_review;

public class InstructionReviewModelOutputException extends InstructionReviewUnavailableException {

    public InstructionReviewModelOutputException(String message, InstructionReviewResult reviewResult, Throwable cause) {
        super(message, reviewResult, cause);
    }
}
