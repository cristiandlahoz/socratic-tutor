package com.wornux.services.document;

import java.util.List;

import com.wornux.ui.ingestion.*;

public record ApproveDocumentCommand(String ingestionId, String title, CourseMaterialCatalog catalog,
        String reviewedMarkdown, List<EditableSegmentViewModel> segments) {}
