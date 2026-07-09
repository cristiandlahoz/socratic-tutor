package com.wornux.services.document;

public record DocumentWorkspaceCard(
        String ingestionId,
        String title,
        String status,
        int segmentCount,
        String catalogLabel,
        String catalogUseWhen) {}
