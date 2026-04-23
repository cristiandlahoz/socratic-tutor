package com.wornux.documentingest;

import java.util.List;

public record DoclingConversionResult(
    String markdown, Integer pageCount, List<DoclingSegmentDraft> segments) {}
