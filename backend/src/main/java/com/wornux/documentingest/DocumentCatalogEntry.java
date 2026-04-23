package com.wornux.documentingest;

import java.util.List;

public record DocumentCatalogEntry(
    String title,
    String topic,
    String summary,
    List<String> tags,
    List<String> entities,
    List<String> questionExamples) {

  public static DocumentCatalogEntry fallback(String filename, String markdown) {
    var title = filename == null || filename.isBlank() ? "Documento PDF" : filename;
    var topic = firstNonBlankLine(markdown);
    return new DocumentCatalogEntry(title, topic, topic, List.of(), List.of(), List.of());
  }

  public DocumentCatalogEntry normalized(int maxTags, int maxQuestionExamples) {
    return new DocumentCatalogEntry(
        clean(title, 96),
        clean(topic, 180),
        clean(summary, 360),
        normalizeList(tags, maxTags, 48),
        normalizeList(entities, 12, 64),
        normalizeList(questionExamples, maxQuestionExamples, 120));
  }

  private static List<String> normalizeList(List<String> values, int limit, int maxChars) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> clean(value, maxChars))
        .distinct()
        .limit(Math.max(0, limit))
        .toList();
  }

  private static String clean(String value, int maxChars) {
    if (value == null || value.isBlank()) {
      return "";
    }
    var normalized = value.replaceAll("\\s+", " ").trim();
    return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars - 3) + "...";
  }

  private static String firstNonBlankLine(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return "Documento PDF indexado";
    }
    for (String line : markdown.split("\\R")) {
      var cleaned = line.replaceFirst("^#{1,6}\\s+", "").trim();
      if (!cleaned.isBlank()) {
        return clean(cleaned, 180);
      }
    }
    return "Documento PDF indexado";
  }
}
