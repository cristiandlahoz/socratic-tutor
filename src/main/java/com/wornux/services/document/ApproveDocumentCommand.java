package com.wornux.services.document;

import java.util.List;

import com.wornux.ui.ingestion.*;

public record ApproveDocumentCommand(Long documentId, String reviewedMarkdown,
        List<EditableSegmentViewModel> segments) {}
