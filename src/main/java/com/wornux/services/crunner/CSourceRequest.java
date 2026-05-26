package com.wornux.services.crunner;

import java.util.Locale;

public record CSourceRequest(String source, String standard, String filename) {

  public static final String DEFAULT_STANDARD = "c17";
  public static final String DEFAULT_FILENAME = "main.c";

  public CSourceRequest {
    source = source == null ? "" : source;
    standard = normalizeStandard(standard);
    filename = normalizeFilename(filename);
  }

  private static String normalizeStandard(String standard) {
    if (standard == null || standard.isBlank()) {
      return DEFAULT_STANDARD;
    }
    return standard.trim().toLowerCase(Locale.ROOT);
  }

  private static String normalizeFilename(String filename) {
    if (filename == null || filename.isBlank()) {
      return DEFAULT_FILENAME;
    }
    var normalized = filename.trim();
    if (normalized.contains("/") || normalized.contains("\\") || !normalized.matches("[A-Za-z0-9._-]+")) {
      throw new IllegalArgumentException("C source filename must be a simple file name");
    }
    return normalized;
  }
}
