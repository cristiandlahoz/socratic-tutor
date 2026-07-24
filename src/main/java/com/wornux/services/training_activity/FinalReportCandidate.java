package com.wornux.services.training_activity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.wornux.data.entities.training_activity.EvidenceStatus;

/** Untrusted structured output from the report model. It is validated before persistence. */
public record FinalReportCandidate(
        @JsonPropertyDescription("Optional model-provided value. The service replaces it with the assignment's authoritative evidence status.")
        EvidenceStatus evidenceStatus,
        @JsonProperty(required = true) @JsonPropertyDescription("Concise evidence-grounded summary for the professor.") String summary,
        @JsonProperty(required = true) List<ReportFinding> strengths,
        @JsonProperty(required = true) List<ReportFinding> weaknesses,
        @JsonProperty(required = true) List<ReportFinding> observations,
        @JsonProperty(required = true) List<String> recommendations) {

    public FinalReportCandidate withEvidenceStatus(EvidenceStatus evidenceStatus) {
        return new FinalReportCandidate(evidenceStatus, summary, strengths, weaknesses, observations, recommendations);
    }

    public record ReportFinding(
            @JsonProperty(required = true) String observation,
            @JsonProperty(required = true) List<EvidenceReference> evidenceReferences) {}

    /**
     * Untrusted model provenance. Excerpts are checked against canonical persisted turns,
     * then intentionally discarded before report persistence to avoid duplicating the transcript.
     */
    public record EvidenceReference(
            @JsonProperty(required = true) Integer turnSequence,
            String questionExcerpt,
            String answerExcerpt) {}
}
