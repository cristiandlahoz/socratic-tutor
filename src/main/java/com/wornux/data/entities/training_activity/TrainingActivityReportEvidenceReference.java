package com.wornux.data.entities.training_activity;

/** Persist only the canonical sequence; report text never duplicates the transcript. */
public record TrainingActivityReportEvidenceReference(int turnSequence) {}
