package com.wornux.chat.profile;

public enum ThemePreference {
  SYSTEM,
  LIGHT,
  DARK;

  public String storageValue() {
    return name().toLowerCase();
  }

  public static ThemePreference fromStorage(String value) {
    if (value == null || value.isBlank()) {
      return SYSTEM;
    }

    return switch (value.trim().toLowerCase()) {
      case "light" -> LIGHT;
      case "dark" -> DARK;
      default -> SYSTEM;
    };
  }
}
