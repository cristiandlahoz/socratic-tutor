package com.wornux.chat.profile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TurnProfileUpdate(
    UUID conversationId,
    UUID turnId,
    List<TopicKey> topicsDetected,
    List<LevelSignal> levelSignals,
    List<MisconceptionObservation> misconceptionsObserved,
    String preferredLanguage,
    HelpMode recommendedHelpMode,
    boolean needsConcreteExamples,
    BigDecimal confidenceDelta,
    List<ToolEvidence> toolEvidence,
    Map<String, Object> signalPayload) {

  public boolean hasProfileMutation() {
    return !topicsDetected.isEmpty()
        || !misconceptionsObserved.isEmpty()
        || confidenceDelta.signum() != 0
        || needsConcreteExamples
        || preferredLanguage != null
        || recommendedHelpMode != null;
  }

  public record LevelSignal(TopicKey topic, SignalDirection direction, String reason) {}

  public record MisconceptionObservation(
      TopicKey topic, String misconceptionKey, String description, BigDecimal confidence) {}

  public record ToolEvidence(String tool, boolean useful, String reason) {}

  public enum SignalDirection {
    UP,
    DOWN
  }
}
