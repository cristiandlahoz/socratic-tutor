package com.wornux.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wornux.config.ApplicationProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ToolUsageAuditServiceTest {

    @Test
    void auditDoesNotCaptureToolReturnByDefault() {
        var turnId = UUID.randomUUID();
        var service = service(false, 96);

        service.audit("readCourseMaterialPage", toolContext(turnId), "cursor_present=true", () ->
                new ToolUsageAuditService.ToolResult<>(Map.of("content", "secret course material"), "content_found=true"));

        var audit = service.drainTurnAudits(turnId).getFirst();
        assertThat(audit.returnCaptured()).isFalse();
        assertThat(audit.toolReturnJson()).isNull();
        assertThat(audit.toolReturnPreview()).isNull();
    }

    @Test
    void auditCapturesRedactedLengthCappedPreviewWhenEnabled() {
        var turnId = UUID.randomUUID();
        var service = service(true, 36);

        service.audit("readCourseMaterialPage", toolContext(turnId), "cursor_present=true", () ->
                new ToolUsageAuditService.ToolResult<>(Map.of("content", "secret course material"), "content_found=true"));

        var audit = service.drainTurnAudits(turnId).getFirst();
        assertThat(audit.returnCaptured()).isTrue();
        assertThat(audit.toolReturnJson()).contains("secret course material");
        assertThat(audit.toolReturnPreview())
                .contains("redacted")
                .doesNotContain("secret course material")
                .hasSizeLessThanOrEqualTo(36);
    }

    private static ToolUsageAuditService service(boolean captureToolReturns, int previewMaxChars) {
        var properties = new ApplicationProperties.Ai.ToolAudit();
        ReflectionTestUtils.setField(properties, "captureToolReturns", captureToolReturns);
        ReflectionTestUtils.setField(properties, "previewMaxChars", previewMaxChars);
        return new ToolUsageAuditService(
                new SimpleMeterRegistry(),
                ObservationRegistry.create(),
                new ObjectMapper(),
                properties);
    }

    private static ToolContext toolContext(UUID turnId) {
        var toolContext = mock(ToolContext.class);
        when(toolContext.getContext()).thenReturn(Map.of(
                ToolContextKeys.GROUP_CLASS_MEMBER_ID, UUID.randomUUID().toString(),
                ToolContextKeys.CONVERSATION_ID, UUID.randomUUID(),
                ToolContextKeys.TURN_ID, turnId));
        return toolContext;
    }
}
