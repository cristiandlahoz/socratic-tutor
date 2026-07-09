package com.wornux.ai.tools;

import com.wornux.config.ApplicationProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ToolUsageAuditService {

    private static final Logger log = LoggerFactory.getLogger(ToolUsageAuditService.class);
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;
    private final ObjectMapper objectMapper;
    private final ApplicationProperties.Ai.ToolAudit toolAuditProperties;
    private final ConcurrentHashMap<UUID, List<ToolExecutionAudit>> auditsByTurnId = new ConcurrentHashMap<>();

    public ToolUsageAuditService(
            MeterRegistry meterRegistry,
            ObservationRegistry observationRegistry,
            ObjectMapper objectMapper,
            ApplicationProperties.Ai.ToolAudit toolAuditProperties) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
        this.objectMapper = objectMapper;
        this.toolAuditProperties = toolAuditProperties;
    }

    public <T> T audit(
            String toolName,
            ToolContext toolContext,
            String inputSummary,
            Supplier<ToolResult<T>> execution) {
        var startedAt = System.nanoTime();
        var ids = ids(toolContext);
        Observation observation = Observation.start("tool." + toolName, observationRegistry);
        try (Observation.Scope ignored = observation.openScope()) {
            ToolResult<T> result = execution.get();
            var capturedReturn = captureToolReturn(result.value());
            var audit = new ToolExecutionAudit(ids.conversationId(),
                    ids.groupClassMemberId(),
                    ids.turnId(),
                    toolName,
                    "success",
                    nanosToMillis(startedAt),
                    inputSummary,
                    result.outputSummary(),
                    capturedReturn.json(),
                    capturedReturn.preview(),
                    capturedReturn.captured(),
                    true,
                    null);
            register(audit);
            meterRegistry.counter("tool.calls.total", "tool.name", toolName, "tool.status", "success").increment();
            meterRegistry.counter("tool.calls.success", "tool.name", toolName).increment();
            Timer.builder("tool.latency")
                    .tag("tool.name", toolName)
                    .register(meterRegistry)
                    .record(audit.latencyMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
            log.info(
                """
                tool_execution tool.name={} tool.status={} tool.latency_ms={} group_class_member_id={}\
                 conversation_id={} turn_id={} model_requested_tool={} input_summary={}\
                 output_summary={} return_captured={} tool_return_preview={} failure_code={}\
                """,
                audit.toolName(),
                audit.status(),
                audit.latencyMs(),
                audit.groupClassMemberId(),
                audit.conversationId(),
                audit.turnId(),
                audit.modelRequested(),
                audit.inputSummary(),
                audit.outputSummary(),
                audit.returnCaptured(),
                nullableLogValue(audit.toolReturnPreview()),
                nullableLogValue(audit.failureCode()));
            return result.value();
        }
        catch (RuntimeException exception) {
            var audit = new ToolExecutionAudit(ids.conversationId(),
                    ids.groupClassMemberId(),
                    ids.turnId(),
                    toolName,
                    "failure",
                    nanosToMillis(startedAt),
                    inputSummary,
                    "error",
                    null,
                    null,
                    false,
                    true,
                    exception.getClass().getSimpleName());
            register(audit);
            meterRegistry.counter("tool.calls.total", "tool.name", toolName, "tool.status", "failure").increment();
            meterRegistry.counter("tool.calls.failure", "tool.name", toolName).increment();
            Timer.builder("tool.latency")
                    .tag("tool.name", toolName)
                    .register(meterRegistry)
                    .record(audit.latencyMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
            log.warn(
                """
                tool_execution tool.name={} tool.status={} tool.latency_ms={} group_class_member_id={}\
                 conversation_id={} turn_id={} model_requested_tool={} input_summary={}\
                 output_summary={} return_captured={} tool_return_preview={} failure_code={}\
                """,
                audit.toolName(),
                audit.status(),
                audit.latencyMs(),
                audit.groupClassMemberId(),
                audit.conversationId(),
                audit.turnId(),
                audit.modelRequested(),
                audit.inputSummary(),
                audit.outputSummary(),
                audit.returnCaptured(),
                nullableLogValue(audit.toolReturnPreview()),
                nullableLogValue(audit.failureCode()));
            throw exception;
        }
        finally {
            observation.stop();
        }
    }

    public List<ToolExecutionAudit> drainTurnAudits(UUID turnId) {
        var audits = auditsByTurnId.remove(turnId);
        return audits == null ? List.of() : List.copyOf(audits);
    }

    private void register(ToolExecutionAudit audit) {
        auditsByTurnId.compute(audit.turnId(), (_, existing) -> {
            var next = existing == null ? new ArrayList<ToolExecutionAudit>() : new ArrayList<>(existing);
            next.add(audit);
            return next;
        });
    }

    private long nanosToMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private CapturedToolReturn captureToolReturn(@Nullable Object value) {
        if (!toolAuditProperties.isCaptureToolReturns()) {
            return new CapturedToolReturn(false, null, null);
        }
        try {
            if (value == null) {
                return new CapturedToolReturn(true, null, null);
            }
            var json = objectMapper.writeValueAsString(value);
            return new CapturedToolReturn(true, json, preview(json));
        }
        catch (JacksonException ex) {
            return new CapturedToolReturn(true,
                    null,
                    cap("tool_return_redacted(serialization_error=%s)".formatted(ex.getClass().getSimpleName())));
        }
    }

    private Object nullableLogValue(@Nullable Object value) {
        return value == null ? "" : value;
    }

    private String preview(String json) {
        var normalized = json.replaceAll("\\s+", " ").trim();
        return cap("tool_return_redacted(chars=%d,sha256=%s)"
                .formatted(normalized.length(), sha256(normalized).substring(0, 12)));
    }

    private String cap(String value) {
        var maxChars = toolAuditProperties.previewMaxChars();
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars - 1) + "…";
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for tool audit previews", exception);
        }
    }

    private ToolInvocationIds ids(ToolContext toolContext) {
        var context = toolContext.getContext();
        return new ToolInvocationIds(
                UUID.fromString(String.valueOf(context.get(ToolContextKeys.GROUP_CLASS_MEMBER_ID))),
                UUID.fromString(String.valueOf(context.get(ToolContextKeys.CONVERSATION_ID))),
                UUID.fromString(String.valueOf(context.get(ToolContextKeys.TURN_ID))));
    }

    public record ToolResult<T>(T value, String outputSummary) {}

    private record CapturedToolReturn(boolean captured, @Nullable String json, @Nullable String preview) {
    }

    private record ToolInvocationIds(UUID groupClassMemberId, UUID conversationId, UUID turnId) {}
}
