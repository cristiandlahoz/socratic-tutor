package com.wornux.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class ToolUsageAuditServiceTest {

  private final ToolUsageAuditService service =
      new ToolUsageAuditService(new SimpleMeterRegistry(), ObservationRegistry.NOOP);

  @Test
  void audit_records_success_and_drains_turn() {
    UUID turnId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    String result =
        service.audit(
            "traceCProgram",
            context(clientId, conversationId, turnId),
            "code_len=32",
            () ->
                new ToolUsageAuditService.ToolResult<>(
                    "ok", "steps=3", new ToolLearningSignal("trace", true, "useful")));

    assertThat(result).isEqualTo("ok");
    assertThat(service.drainTurnAudits(turnId))
        .singleElement()
        .satisfies(
            audit -> {
              assertThat(audit.status()).isEqualTo("success");
              assertThat(audit.usefulForProfile()).isTrue();
            });
  }

  @Test
  void audit_records_failures() {
    UUID turnId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                service.audit(
                    "evaluateStudentAnswer",
                    context(clientId, conversationId, turnId),
                    "answer_len=14",
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(service.drainTurnAudits(turnId))
        .singleElement()
        .satisfies(audit -> assertThat(audit.failureCode()).isEqualTo("IllegalStateException"));
  }

  private ToolContext context(UUID clientId, UUID conversationId, UUID turnId) {
    return new ToolContext(
        Map.of(
            ToolUsageAuditService.CLIENT_ID, clientId,
            ToolUsageAuditService.CONVERSATION_ID, conversationId,
            ToolUsageAuditService.TURN_ID, turnId,
            ToolUsageAuditService.PROFILE_VERSION, 7L));
  }
}
