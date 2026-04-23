package com.wornux.documentingest;

import java.util.List;
import java.util.UUID;

public record ApproveDocumentCommand(
    UUID clientId, UUID documentId, String reviewedMarkdown, List<EditableSegmentVm> segments) {}
