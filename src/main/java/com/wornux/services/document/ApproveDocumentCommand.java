package com.wornux.services.document;

import java.util.List;

import com.wornux.ui.ingestion.*;

public record ApproveDocumentCommand(String ingestionId, String title, String reviewedMarkdown,
        List<EditableSegmentViewModel> segments) {}
