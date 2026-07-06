package com.wornux.ai.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record ToolExecutionAudit(UUID conversationId, UUID groupClassMemberId, UUID turnId, String toolName,
        String status, long latencyMs, String inputSummary, String outputSummary, @Nullable String toolReturnJson,
        @Nullable String toolReturnPreview, boolean returnCaptured, boolean modelRequested, @Nullable String failureCode) {

    public Map<String, Object> toMap() {
        Map<String, Object> auditFields = new LinkedHashMap<>();
        auditFields.put("toolName", toolName);
        auditFields.put("status", status);
        auditFields.put("latencyMs", latencyMs);
        auditFields.put("inputSummary", inputSummary);
        auditFields.put("outputSummary", outputSummary);
        auditFields.put("toolReturnJson", toolReturnJson);
        auditFields.put("toolReturnPreview", toolReturnPreview);
        auditFields.put("returnCaptured", returnCaptured);
        auditFields.put("modelRequested", modelRequested);
        auditFields.put("failureCode", failureCode);
        return auditFields;
    }
}
