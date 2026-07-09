package com.wornux.services.training_activity;

public record TrainingActivitySaveCommand(
        String title,
        String instructions,
        boolean safeBrowserEnabled,
        String confirmedReviewHash) {

    public TrainingActivitySaveCommand(
            String title,
            String instructions,
            boolean safeBrowserEnabled) {
        this(title, instructions, safeBrowserEnabled, "");
    }
}
