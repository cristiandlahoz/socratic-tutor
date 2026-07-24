package com.wornux.services.training_activity;

/** Bounded model-output failure code safe for durable job retry classification. */
public class AdaptiveTutorModelOutputException extends IllegalStateException {
    private final String failureCode;

    public AdaptiveTutorModelOutputException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public AdaptiveTutorModelOutputException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
