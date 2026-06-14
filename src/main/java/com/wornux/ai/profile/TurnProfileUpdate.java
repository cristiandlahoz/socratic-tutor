package com.wornux.ai.profile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TurnProfileUpdate(UUID conversationId, UUID turnId, List<String> topicsDetected,
        List<LevelSignal> levelSignals, List<MisconceptionObservation> misconceptionsObserved,
        List<ToolEvidence> toolEvidence, Map<String, Object> signalPayload) {

    public boolean hasProfileMutation() {
        return !topicsDetected.isEmpty() || !misconceptionsObserved.isEmpty();
    }

    public record LevelSignal(String topicKey, SignalDirection direction, String reason) {}

    public record MisconceptionObservation(String topicKey, String misconceptionKey, String description) {}

    public record ToolEvidence(String tool, boolean useful, String reason) {}

    public enum SignalDirection {
        UP, DOWN
    }
}
