package com.wornux.chat.profile;

import com.wornux.chat.StoredChatMessage;
import com.wornux.chat.tools.ToolExecutionAudit;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TurnProfileInferenceServiceTest {

    private final TurnProfileInferenceService service = new TurnProfileInferenceService();

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
                )));

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
                List.of());

        assertThat(update.topicsDetected()).contains(TopicKey.ARRAYS);
    }
}
