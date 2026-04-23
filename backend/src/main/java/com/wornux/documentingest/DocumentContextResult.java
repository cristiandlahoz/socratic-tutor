package com.wornux.documentingest;

import java.util.List;

public record DocumentContextResult(List<DocumentSearchHit> hits, boolean contextFound) {}
