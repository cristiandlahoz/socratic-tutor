package com.wornux.domain.profile;

public enum ThemePreference {
  SYSTEM,
  LIGHT,
  DARK;

  public String storageValue() {
    return name().toLowerCase();
  }
}
