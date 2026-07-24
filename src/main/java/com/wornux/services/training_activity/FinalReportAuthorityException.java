package com.wornux.services.training_activity;

/** Safe deterministic failure for an impossible missing final-report authority. */
public final class FinalReportAuthorityException extends IllegalStateException {

    public static final String FAILURE_CODE = "MISSING_AUTHORITATIVE_FINAL_REPORT_EVIDENCE_STATUS";

    public FinalReportAuthorityException() {
        super("Final report requires an authoritative evidence status.");
    }
}
