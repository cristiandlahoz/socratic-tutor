package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdaptiveTutorTranscriptEvidenceTest {

    @Test
    void answerSignalsUseSharedThresholdsForReasoningExamplesCodeAndCorrections() {
        var signals = AdaptiveTutorTranscriptEvidence.answerSignals(
                "Por ejemplo, el código compila porque solo hay una instrucción; printf(i); no necesita llaves.");

        assertThat(signals.blank()).isFalse();
        assertThat(signals.example()).isTrue();
        assertThat(signals.reasoning()).isTrue();
        assertThat(signals.code()).isTrue();
        assertThat(signals.correctionOfTutorPremise()).isTrue();
        assertThat(signals.hasUsefulEvidence()).isTrue();
    }

    @Test
    void answerSignalsMarkBriefUnknownAnswersWithoutUsefulEvidence() {
        var signals = AdaptiveTutorTranscriptEvidence.answerSignals("No sé");

        assertThat(signals.blank()).isFalse();
        assertThat(signals.unknown()).isTrue();
        assertThat(signals.veryBrief()).isTrue();
        assertThat(signals.hasUsefulEvidence()).isFalse();
    }
}
