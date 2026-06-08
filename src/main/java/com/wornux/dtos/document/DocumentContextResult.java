package com.wornux.dtos.document;

import java.util.List;

public record DocumentContextResult(List<DocumentSearchHit> hits, boolean contextFound) {}
