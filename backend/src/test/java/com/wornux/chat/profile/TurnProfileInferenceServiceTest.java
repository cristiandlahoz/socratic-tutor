package com.wornux.chat.profile;

import com.wornux.chat.StoredChatMessage;
import com.wornux.chat.questions.StudentQuestionAnswer;
import com.wornux.chat.questions.StudentQuestionOption;
import com.wornux.chat.questions.StudentQuestionResponse;
import com.wornux.chat.questions.StudentQuestionSet;
import com.wornux.chat.questions.StudentQuestion;
import com.wornux.chat.tools.QuestionInteractionService;
import com.wornux.chat.tools.ToolExecutionAudit;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TurnProfileInferenceServiceTest {

    private final TurnProfileInferenceService service = new TurnProfileInferenceService(new QuestionAnswerProfileSignalService());

    @Test
    void infer_detects_topics_examples_and_misconceptions() {
        var update = service.infer(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "No entiendo la diferencia entre contador y acumulador en un for, dame un ejemplo paso a paso",
                "Vamos a mirarlo con una traza corta.",
                List.of(new StoredChatMessage(MessageType.USER, "ayuda con loops", Instant.now())),
                List.of(new ToolExecutionAudit(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "traceCProgram",
                        "success",
                        120,
                        "code_len=80",
                        "supported=true steps=4",
                        true,
                        true,
                        2L,
                        null
                )),
                List.of());

        assertThat(update.topicsDetected()).contains(TopicKey.LOOPS);
        assertThat(update.needsConcreteExamples()).isTrue();
        assertThat(update.misconceptionsObserved())
                .extracting(TurnProfileUpdate.MisconceptionObservation::misconceptionKey)
                .contains("counter_vs_accumulator");
        assertThat(update.levelSignals())
                .extracting(TurnProfileUpdate.LevelSignal::direction)
                .contains(TurnProfileUpdate.SignalDirection.DOWN);
        assertThat(update.preferredLanguage()).isEqualTo("es");
    }

    @Test
    void infer_uses_memory_when_prompt_is_sparse() {
        var update = service.infer(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "help",
                "Let's focus on arrays.",
                List.of(new StoredChatMessage(MessageType.USER, "why does my array index start at 0", Instant.now())),
                List.of(),
                List.of());

        assertThat(update.topicsDetected()).contains(TopicKey.ARRAYS);
    }

    @Test
    void infer_merges_interactive_question_signals() {
        var routing = new QuestionInteractionService.QuestionRouting(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var interaction = new QuestionInteractionService.CompletedQuestionInteraction(
                routing,
                new StudentQuestionSet("Diagnostico", "diagnosis", StudentQuestionSet.ProfileImpact.PEDAGOGICAL, List.of(
                        new StudentQuestion("q1", "Nivel", "Como te sientes con los loops?", List.of(
                                new StudentQuestionOption("Muy perdido", "Necesito empezar desde cero"),
                                new StudentQuestionOption("Con algo de base", "Entiendo lo basico pero me pierdo")),
                                false,
                                true))),
                new StudentQuestionResponse(List.of(
                        new StudentQuestionAnswer("q1", List.of("Muy perdido"), "Necesito un ejemplo paso a paso de for"))),
                Instant.now());

        var update = service.infer(
                routing.conversationId(),
                routing.turnId(),
                "ayuda",
                "",
                List.of(),
                List.of(),
                List.of(interaction));

        assertThat(update.topicsDetected()).contains(TopicKey.LOOPS);
        assertThat(update.needsConcreteExamples()).isTrue();
        assertThat(update.recommendedHelpMode()).isEqualTo(HelpMode.GUIDED);
        assertThat(update.levelSignals())
                .extracting(TurnProfileUpdate.LevelSignal::reason)
                .contains("interactive_question_low_confidence");
    }
}
