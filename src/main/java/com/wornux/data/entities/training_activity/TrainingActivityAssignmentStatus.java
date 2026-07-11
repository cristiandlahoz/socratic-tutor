package com.wornux.data.entities.training_activity;

public enum TrainingActivityAssignmentStatus {
    ASSIGNED, STARTING, WAITING_FOR_ANSWER, WAITING_FOR_TUTOR, TEMPORARILY_UNAVAILABLE, SUBMITTED, SKIPPED, EXPIRED, EXCUSED;

    public boolean isTerminal() {
        return switch (this) {
            case SUBMITTED, EXPIRED, EXCUSED, SKIPPED -> true;
            case ASSIGNED, STARTING, WAITING_FOR_ANSWER, WAITING_FOR_TUTOR, TEMPORARILY_UNAVAILABLE -> false;
        };
    }
}
