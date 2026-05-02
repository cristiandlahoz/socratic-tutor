package com.wornux.ai.tools;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
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
    String toolReturnJson,
    String toolReturnPreview,
    boolean payloadCaptured,
    boolean modelRequested,
    boolean usefulForProfile,
    long profileSnapshotVersion,
    String failureCode) {

  public Map<String, Object> toMap() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("toolName", toolName);
    payload.put("status", status);
    payload.put("latencyMs", latencyMs);
    payload.put("inputSummary", inputSummary);
    payload.put("outputSummary", outputSummary);
    payload.put("toolReturnJson", toolReturnJson);
    payload.put("toolReturnPreview", toolReturnPreview);
    payload.put("payloadCaptured", payloadCaptured);
    payload.put("modelRequested", modelRequested);
    payload.put("usefulForProfile", usefulForProfile);
    payload.put("profileSnapshotVersion", profileSnapshotVersion);
    payload.put("failureCode", failureCode);
    return payload;
  }
}
