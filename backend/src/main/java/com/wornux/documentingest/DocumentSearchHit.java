package com.wornux.documentingest;

import java.util.List;
import java.util.UUID;

public record DocumentSearchHit(
    String segmentId,
    UUID documentId,
    String filename,
    String documentTitle,
    String documentTopic,
    List<String> documentTags,
    String headingPath,
    String excerpt,
    Double score,
    Integer ordinal,
    Integer pageNumber,
    List<Integer> pageNumbers,
    List<String> captions) {}
