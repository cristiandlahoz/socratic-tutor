package com.wornux.dtos.document;

import java.util.List;

public record DocumentSearchHit(String segmentId, String documentId, String filename, String documentTitle,
        String documentTopic, List<String> documentTags, String headingPath, String excerpt, Double score,
        Integer ordinal, Integer pageNumber, List<Integer> pageNumbers, List<String> captions) {}
