package com.wornux.documentingest;

import java.util.UUID;

public record EditableSegmentVm(
    UUID id,
    int ordinal,
    String headingPath,
    String content,
    boolean approved,
    boolean edited,
    Integer charCount,
    Integer tokenCount,
    Integer pageNumber) {

  public EditableSegmentVm withContent(String nextContent) {
    String safeContent = nextContent == null ? "" : nextContent;
    boolean changed = !safeContent.equals(content);
    return new EditableSegmentVm(
        id,
        ordinal,
        headingPath,
        safeContent,
        approved,
        edited || changed,
        safeContent.length(),
        approximateTokens(safeContent),
        pageNumber);
  }

  public static int approximateTokens(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    return text.trim().split("\\s+").length;
  }
}
