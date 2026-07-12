package com.wornux.data.entities.training_activity;

import java.util.List;

/** A validated professor-facing observation backed by authoritative canonical turns. */
public record TrainingActivityReportFinding(String observation, List<TrainingActivityReportEvidenceReference> evidenceReferences) {}
