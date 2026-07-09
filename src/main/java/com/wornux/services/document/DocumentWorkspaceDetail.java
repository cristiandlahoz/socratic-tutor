package com.wornux.services.document;

import java.util.List;

import com.wornux.ui.ingestion.EditableSegmentViewModel;

public record DocumentWorkspaceDetail(
        String ingestionId,
        String title,
        String status,
        CourseMaterialCatalog catalog,
        String markdown,
        List<EditableSegmentViewModel> segments,
        List<String> vectorIds) {}
