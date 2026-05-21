package com.wornux.presentation.documentingest;

import java.util.List;
import java.util.UUID;

public record EditableSegmentVm(UUID id, int ordinal, String headingPath, String content, boolean approved,
        boolean edited, Integer charCount, Integer tokenCount, Integer pageNumber, List<Integer> pageNumbers,
        List<String> captions, List<String> docItems, String rawText, String chunker) {

    public EditableSegmentVm withContent(String nextContent) {
        String safeContent = nextContent == null ? "" : nextContent;
        boolean changed = !safeContent.equals(content);
        return new EditableSegmentVm(id,
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
