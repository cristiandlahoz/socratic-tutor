package com.wornux.data.entities.training_activity;

public enum TrainingActivityAssignmentStatus {
    ASSIGNED, STARTED, SUBMITTED, SKIPPED, EXPIRED, EXCUSED;

    public boolean isTerminal() {
        return switch (this) {
            case SUBMITTED, EXPIRED, EXCUSED, SKIPPED -> true;
            case ASSIGNED, STARTED -> false;
        };
    }
}
