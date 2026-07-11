package com.wornux.data.entities.training_activity;

public enum SafeBrowserSessionStatus {
    PENDING,
    ACTIVE,
    VIOLATED,
    EXPIRED,
    ENDED;

    public boolean isTerminal() {
        return this == VIOLATED || this == EXPIRED || this == ENDED;
    }
}
