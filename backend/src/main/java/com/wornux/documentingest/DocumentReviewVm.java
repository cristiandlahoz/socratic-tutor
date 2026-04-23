package com.wornux.documentingest;

import java.util.List;
import java.util.UUID;

public record DocumentReviewVm(
    UUID documentId,
    UUID jobId,
    String filename,
    DocumentStatus status,
    String stageLabel,
    String markdown,
    List<EditableSegmentVm> segments,
    boolean indexed) {}
