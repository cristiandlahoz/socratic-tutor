package com.wornux.dtos.document;

import org.jspecify.annotations.Nullable;

public record DocumentPageResult(@Nullable String source, String content, @Nullable String previousCursor,
        @Nullable String nextCursor, boolean hasPrevious, boolean hasNext) {}
