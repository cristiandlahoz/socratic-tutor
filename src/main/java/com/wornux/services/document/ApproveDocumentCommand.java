package com.wornux.services.document;

import java.util.List;
import java.util.UUID;

import com.wornux.ui.ingestion.*;

public record ApproveDocumentCommand(UUID clientId, UUID documentId, String reviewedMarkdown,
        List<EditableSegmentViewModel> segments) {}
