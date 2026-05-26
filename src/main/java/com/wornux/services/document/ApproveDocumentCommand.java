package com.wornux.services.document;

import com.wornux.ui.ingestion.*;
import java.util.List;
import java.util.UUID;

public record ApproveDocumentCommand(
    UUID clientId, UUID documentId, String reviewedMarkdown, List<EditableSegmentViewModel> segments) {}
