package com.wornux.chat.profile;

import com.wornux.chat.questions.StudentQuestion;
import com.wornux.chat.questions.StudentQuestionAnswer;
import com.wornux.chat.questions.StudentQuestionOption;
import com.wornux.chat.questions.StudentQuestionResponse;
import com.wornux.chat.questions.StudentQuestionSet;
import com.wornux.chat.tools.QuestionInteractionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionAnswerProfileSignalServiceTest {

    private final QuestionAnswerProfileSignalService service = new QuestionAnswerProfileSignalService();

    @Test
    void interpret_extracts_topics_help_mode_and_examples() {
        var routing = new QuestionInteractionService.QuestionRouting(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var interaction = new QuestionInteractionService.CompletedQuestionInteraction(
                routing,
                new StudentQuestionSet("Diagnostico", "diagnosis", StudentQuestionSet.ProfileImpact.PEDAGOGICAL, List.of(
                        new StudentQuestion("q1", "Ayuda", "Que necesitas ahora?", List.of(
                                new StudentQuestionOption("Pistas guiadas", "Prefiero hints antes que respuesta"),
                                new StudentQuestionOption("Loops", "Me pierdo con for y while")),
                                true))),
                new StudentQuestionResponse(List.of(
                        new StudentQuestionAnswer("q1", List.of("Pistas guiadas", "Loops"), "Necesito un ejemplo paso a paso"))),
                Instant.now());

        var signals = service.interpret(List.of(interaction));

        assertThat(signals.topics()).contains(TopicKey.LOOPS);
        assertThat(signals.recommendedHelpMode()).isEqualTo(HelpMode.GUIDED);
        assertThat(signals.needsConcreteExamples()).isTrue();
        assertThat(signals.payload()).containsKey("interactiveAnswers");
    }

    @Test
    void interpret_ignores_non_pedagogical_interactions() {
        var routing = new QuestionInteractionService.QuestionRouting(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var interaction = new QuestionInteractionService.CompletedQuestionInteraction(
                routing,
                new StudentQuestionSet("Preferencia", "preference", StudentQuestionSet.ProfileImpact.NONE, List.of(
                        new StudentQuestion("q1", "Formato", "Como quieres seguir?", List.of(
                                new StudentQuestionOption("Analogias", "Quiero una analogia"),
                                new StudentQuestionOption("Codigo", "Quiero ver codigo")),
                                false))),
                new StudentQuestionResponse(List.of(
                        new StudentQuestionAnswer("q1", List.of("Analogias"), ""))),
                Instant.now());

        var signals = service.interpret(List.of(interaction));

        assertThat(signals.topics()).isEmpty();
        assertThat(signals.levelSignals()).isEmpty();
        assertThat(signals.recommendedHelpMode()).isNull();
        assertThat(signals.payload()).isEmpty();
    }
}
