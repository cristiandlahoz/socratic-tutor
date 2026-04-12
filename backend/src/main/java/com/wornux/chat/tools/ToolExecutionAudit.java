package com.wornux.chat.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ToolExecutionAudit(
        UUID conversationId,
        UUID clientId,
        UUID turnId,
        String toolName,
        String status,
        long latencyMs,
        String inputSummary,
        String outputSummary,
        boolean modelRequested,
        boolean usefulForProfile,
        long profileSnapshotVersion,
        String failureCode
) {

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", toolName);
        payload.put("status", status);
        payload.put("latencyMs", latencyMs);
        payload.put("inputSummary", inputSummary);
        payload.put("outputSummary", outputSummary);
        payload.put("modelRequested", modelRequested);
        payload.put("usefulForProfile", usefulForProfile);
        payload.put("profileSnapshotVersion", profileSnapshotVersion);
        payload.put("failureCode", failureCode);
        return payload;
    }
}
