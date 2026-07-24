package com.wornux.services.training_activity;

import java.util.Objects;

/** Safe marker for canonical final-report evidence validation failures. */
public final class FinalReportCandidateValidationException extends IllegalArgumentException {

    public enum Reason {
        NULL_CANDIDATE,
        MISSING_EVIDENCE_STATUS,
        EVIDENCE_STATUS_MISMATCH,
        INVALID_SUMMARY,
        MISSING_FINDING_COLLECTIONS,
        MISSING_RECOMMENDATIONS,
        TOO_MANY_RECOMMENDATIONS,
        WEAK_EVIDENCE_CONTRACT_MISMATCH,
        NO_EVIDENCE_CONTRACT_MISMATCH,
        INVALID_RECOMMENDATION,
        FINDING_LIMIT_EXCEEDED_OR_INVALID_TEXT,
        MISSING_EVIDENCE_REFERENCE,
        INVALID_TURN_REFERENCE,
        QUESTION_EXCERPT_MISMATCH,
        ANSWER_EXCERPT_MISMATCH
    }

    private final Reason reason;

    public FinalReportCandidateValidationException(Reason reason) {
        super("Final report candidate failed canonical evidence validation.");
        this.reason = Objects.requireNonNull(reason);
    }

    public Reason reason() {
        return reason;
    }
}
