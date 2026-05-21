package com.wornux.domain.document;

import java.util.List;

public record DoclingSegmentDraft(
    int ordinal,
    String headingPath,
    String content,
    Integer tokenCount,
    Integer pageNumber,
    List<Integer> pageNumbers,
    List<String> captions,
    List<String> docItems,
    String rawText) {}
