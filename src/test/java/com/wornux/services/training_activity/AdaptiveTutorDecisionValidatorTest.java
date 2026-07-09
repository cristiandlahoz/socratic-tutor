package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.data.entities.training_activity.AnswerQuality;
import com.wornux.data.entities.training_activity.CoverageStatus;
import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.PedagogicalMove;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdaptiveTutorDecisionValidatorTest {

    private final AdaptiveTutorDecisionValidator validator = new AdaptiveTutorDecisionValidator();

    @Test
    void rejectsGenericErrorPremiseForValidBracedLoop() {
        assertThatThrownBy(() -> validator.validate(questionDecision("""
                Observa esta variante:

                ```c
                for (int i = 0; i < 3; i++) {
                    printf("%d", i);
                }
                ```

                ¿Dónde está el error?
                """), emptyEvidence()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("syntax-error premise");
    }

    @Test
    void rejectsGenericErrorPremiseForValidBracelessLoop() {
        assertThatThrownBy(() -> validator.validate(questionDecision("""
                Observa esta variante:

                ```c
                for (int i = 0; i < 3; i++)
                    printf("%d", i);
                ```

                ¿Cuál es el error?
                """), emptyEvidence()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("syntax-error premise");
    }

    @Test
    void rejectsWhyItFailsPremiseForValidBracedLoop() {
        assertThatThrownBy(() -> validator.validate(questionDecision("""
                Observa esta variante:

                ```c
                for (int i = 0; i < 3; i++) {
                    printf("%d", i);
                }
                ```

                ¿Por qué falla?
                """), emptyEvidence()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("syntax-error premise");
    }

    @Test
    void rejectsWhyItShowsErrorPremiseForValidBracelessLoop() {
        assertThatThrownBy(() -> validator.validate(questionDecision("""
                Observa esta variante:

                ```c
                for (int i = 0; i < 3; i++)
                    printf("%d", i);
                ```

                ¿Por qué marca error?
                """), emptyEvidence()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("syntax-error premise");
    }

    @Test
    void doesNotRejectBrokenLoopAsFalsePremiseWhenPrintfIsActuallyBroken() {
        var decision = questionDecision("""
                Observa esta variante:

                ```c
                for (int i = 0; i < 3; i++) {
                    printf("%d", i)
                }
                ```

                ¿Dónde está el error?
                """);

        assertThat(validator.validate(decision, emptyEvidence())).isSameAs(decision);
    }

    @Test
    void requiresFencedBlocksForInlineForLoopWithoutSpace() {
        assertThatThrownBy(() -> validator.validate(
                questionDecision("En este for(int i = 0; i < 3; i++) printf(\"%d\", i); ¿qué ocurre?"),
                emptyEvidence()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fenced Markdown code blocks");
    }

    private static AdaptiveTutorTranscriptEvidence emptyEvidence() {
        return AdaptiveTutorTranscriptEvidence.from(List.of());
    }

    private static AdaptiveTutorDecision questionDecision(String questionText) {
        return new AdaptiveTutorDecision(
                TutorDecisionType.QUESTION,
                AnswerQuality.GOOD,
                EvidenceStatus.PARTIAL_EVIDENCE,
                CoverageStatus.PARTIAL,
                PedagogicalMove.ASK_FOR_CLARITY,
                true,
                List.of(),
                List.of("example"),
                false,
                questionText,
                "Needs one more question.");
    }
}
