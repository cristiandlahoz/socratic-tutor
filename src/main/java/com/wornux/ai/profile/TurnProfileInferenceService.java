package com.wornux.ai.profile;

import com.wornux.ai.tools.ToolExecutionAudit;
import com.wornux.data.enums.HelpMode;
import com.wornux.dtos.chat.StoredChatMessage;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TurnProfileInferenceService {

  private static final Pattern SPANISH_PATTERN =
      Pattern.compile(
          "\\b(que|como|porque|explica|ayuda|dame|paso a paso)\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern EXAMPLE_PATTERN =
      Pattern.compile(
          "\\b(ejemplo|example|paso a paso|step by step|traza|trace)\\b",
          Pattern.CASE_INSENSITIVE);

  public TurnProfileUpdate infer(
      UUID conversationId,
      UUID turnId,
      String userInput,
      String assistantResponse,
      List<StoredChatMessage> memoryWindow,
      List<ToolExecutionAudit> toolAudits) {
    var preferredLanguage =
        SPANISH_PATTERN.matcher(userInput == null ? "" : userInput).find() ? "es" : "en";
    boolean needsConcreteExamples =
        EXAMPLE_PATTERN.matcher(userInput == null ? "" : userInput).find()
            || toolAudits.stream().anyMatch(ToolExecutionAudit::usefulForProfile);
    var recommendedHelpMode =
        toolAudits.stream().anyMatch(ToolExecutionAudit::usefulForProfile) ? HelpMode.GUIDED : null;
    var toolEvidence =
        toolAudits.stream()
            .map(
                audit ->
                    new TurnProfileUpdate.ToolEvidence(
                        audit.toolName(), audit.usefulForProfile(), audit.outputSummary()))
            .toList();

    Map<String, Object> signalPayload = new LinkedHashMap<>();
    signalPayload.put("preferredLanguage", preferredLanguage);
    signalPayload.put("needsConcreteExamples", needsConcreteExamples);
    signalPayload.put("toolEvidence", toolAudits.stream().map(ToolExecutionAudit::toMap).toList());
    signalPayload.put("source", "turn_shadow_signal");

    return new TurnProfileUpdate(
        conversationId,
        turnId,
        List.of(),
        List.of(),
        List.of(),
        preferredLanguage,
        recommendedHelpMode,
        needsConcreteExamples,
        BigDecimal.ZERO,
        toolEvidence,
        signalPayload);
  }
}
