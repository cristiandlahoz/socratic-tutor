package com.wornux.dtos.document;

import java.util.List;

public record DoclingConversionResult(String markdown, Integer pageCount, List<DoclingSegmentDraft> segments) {}
