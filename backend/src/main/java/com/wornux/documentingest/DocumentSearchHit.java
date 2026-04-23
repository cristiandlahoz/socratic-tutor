package com.wornux.documentingest;

import java.util.UUID;

public record DocumentSearchHit(
    String segmentId,
    UUID documentId,
    String filename,
    String headingPath,
    String excerpt,
    Double score,
    Integer ordinal,
    Integer pageNumber) {}
