package com.wornux.ui.ingestion;

import java.util.List;

public record EditableSegmentViewModel(String id, int ordinal, String headingPath, String content, boolean approved,
        boolean edited, Integer charCount, Integer tokenCount, Integer pageNumber, List<Integer> pageNumbers,
        List<String> captions, List<String> docItems, String rawText, String chunker) {

    public EditableSegmentViewModel withContent(String nextContent) {
        String safeContent = nextContent == null ? "" : nextContent;
        boolean changed = !safeContent.equals(content);
        return new EditableSegmentViewModel(id,
                ordinal,
                headingPath,
                safeContent,
                approved,
                edited || changed,
                safeContent.length(),
                approximateTokens(safeContent),
                pageNumber,
                pageNumbers,
                captions,
                docItems,
                rawText,
                chunker);
    }

    public static int approximateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
