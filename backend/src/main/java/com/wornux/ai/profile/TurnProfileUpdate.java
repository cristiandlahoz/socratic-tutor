package com.wornux.ai.profile;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
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
