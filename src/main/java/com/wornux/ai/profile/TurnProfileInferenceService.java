package com.wornux.ai.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.wornux.ai.tools.ToolExecutionAudit;
import com.wornux.dtos.chat.StoredChatMessage;

@Service
public class TurnProfileInferenceService {

  public TurnProfileUpdate infer(
      UUID conversationId,
      UUID turnId,
      String userInput,
      String assistantResponse,
      List<StoredChatMessage> memoryWindow,
      List<ToolExecutionAudit> toolAudits) {

    var toolEvidence =
        toolAudits.stream()
            .map(
                audit ->
                    new TurnProfileUpdate.ToolEvidence(
                        audit.toolName(), audit.usefulForProfile(), audit.outputSummary()))
            .toList();

    Map<String, Object> signalPayload = new LinkedHashMap<>();
    signalPayload.put("toolEvidence", toolAudits.stream().map(ToolExecutionAudit::toMap).toList());
    signalPayload.put("source", "turn_shadow_signal");

    return new TurnProfileUpdate(
        conversationId,
        turnId,
        List.of(),
        List.of(),
        List.of(),
        toolEvidence,
        signalPayload);
  }
}
