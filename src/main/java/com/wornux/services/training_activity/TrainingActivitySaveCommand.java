package com.wornux.services.training_activity;

import java.util.UUID;

public record TrainingActivitySaveCommand(
        String title,
        String instructions,
        boolean safeBrowserEnabled,
        String confirmedReviewHash,
        UUID reviewCandidateId) {

    public TrainingActivitySaveCommand(
            String title,
            String instructions,
            boolean safeBrowserEnabled) {
        this(title, instructions, safeBrowserEnabled, "");
    }

    public TrainingActivitySaveCommand(
            String title,
            String instructions,
            boolean safeBrowserEnabled,
            String confirmedReviewHash) {
        this(title, instructions, safeBrowserEnabled, confirmedReviewHash, UUID.randomUUID());
    }
}
