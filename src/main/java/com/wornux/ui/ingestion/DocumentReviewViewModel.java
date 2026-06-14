package com.wornux.ui.ingestion;

import java.util.List;
import java.util.UUID;

import com.wornux.data.enums.*;

public record DocumentReviewViewModel(UUID documentId, Long jobId, String filename, DocumentStatus status,
        String stageLabel, String markdown, List<EditableSegmentViewModel> segments, boolean indexed) {}
