package com.wornux.ui.ingestion;

import com.wornux.data.enums.*;
import java.util.List;
import java.util.UUID;

public record DocumentReviewViewModel(
    UUID documentId,
    UUID jobId,
    String filename,
    DocumentStatus status,
    String stageLabel,
    String markdown,
    List<EditableSegmentViewModel> segments,
    boolean indexed) {}
