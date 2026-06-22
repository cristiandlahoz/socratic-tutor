package com.wornux.services.document;

import java.util.List;

import com.wornux.ui.ingestion.*;

public record ApproveDocumentCommand(java.util.UUID clientId, Long documentId, String reviewedMarkdown,
        List<EditableSegmentViewModel> segments) {}
