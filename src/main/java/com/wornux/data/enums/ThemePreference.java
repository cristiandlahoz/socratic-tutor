package com.wornux.data.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ThemePreference {
    SYSTEM, LIGHT, DARK;

    @JsonValue
    public String storageValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ThemePreference fromStorageValue(String value) {
        if (value == null) {
            return SYSTEM;
        }
        return valueOf(value.toUpperCase());
    }
}
