package com.wornux.dtos.document;

public record DocumentPageResult(String source, String content, String previousCursor, String nextCursor,
        boolean hasPrevious, boolean hasNext) {}
