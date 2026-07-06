package com.wornux.dtos.document;

import org.jspecify.annotations.Nullable;

public record DocumentSearchHit(@Nullable String source, String preview, String readCursor) {}
