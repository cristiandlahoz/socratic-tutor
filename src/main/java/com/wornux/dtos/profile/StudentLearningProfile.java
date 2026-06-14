package com.wornux.dtos.profile;

import java.time.Instant;
import java.util.List;

public record StudentLearningProfile(List<LearningSignal> observedCompetencies, List<LearningSignal> strengths,
        List<LearningSignal> activeMisconceptions, List<LearningSignal> weakConcepts, List<String> helpPreferences,
        String preferredLanguage, List<String> recentEvidenceIds, List<String> uncertaintyNotes,
        List<String> tutorAdaptationGuidance) {

    public StudentLearningProfile {
        observedCompetencies = observedCompetencies == null ? List.of() : List.copyOf(observedCompetencies);
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
        activeMisconceptions = activeMisconceptions == null ? List.of() : List.copyOf(activeMisconceptions);
        weakConcepts = weakConcepts == null ? List.of() : List.copyOf(weakConcepts);
        helpPreferences = helpPreferences == null ? List.of() : List.copyOf(helpPreferences);
        preferredLanguage = preferredLanguage == null || preferredLanguage.isBlank() ? "es" : preferredLanguage;
        recentEvidenceIds = recentEvidenceIds == null ? List.of() : List.copyOf(recentEvidenceIds);
        uncertaintyNotes = uncertaintyNotes == null ? List.of() : List.copyOf(uncertaintyNotes);
        tutorAdaptationGuidance = tutorAdaptationGuidance == null ? List.of() : List.copyOf(tutorAdaptationGuidance);
    }

    public static StudentLearningProfile empty(String preferredLanguage) {
        return new StudentLearningProfile(List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                preferredLanguage,
                List.of(),
                List.of("No evaluation evidence has been collected yet."),
                List.of());
    }

    public boolean hasEvidence() {
        return !observedCompetencies.isEmpty()
                || !strengths.isEmpty()
                || !activeMisconceptions.isEmpty()
                || !weakConcepts.isEmpty();
    }

    public record LearningSignal(String key, String label, String evidenceSource, int evidenceCount,
            Instant lastObservedAt, String rubricMatch, boolean hasContradictions, boolean needsMoreEvidence) {}
}
