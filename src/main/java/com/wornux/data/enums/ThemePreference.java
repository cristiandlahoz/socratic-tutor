package com.wornux.data.enums;

public enum ThemePreference {
  SYSTEM,
  LIGHT,
  DARK;

  public String storageValue() {
    return name().toLowerCase();
  }
}
