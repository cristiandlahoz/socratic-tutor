package com.wornux.ai.tools;

import com.wornux.ai.config.TutorAiProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ToolUsageAuditService {

  private static final Logger log = LoggerFactory.getLogger(ToolUsageAuditService.class);
  public static final String CLIENT_ID = "clientId";
  public static final String CONVERSATION_ID = "conversationId";
  public static final String TURN_ID = "turnId";
  public static final String PROFILE_VERSION = "profileVersion";

  private final MeterRegistry meterRegistry;
  private final ObservationRegistry observationRegistry;
  private final ObjectMapper objectMapper;
  private final TutorAiProperties tutorAiProperties;
  private final ConcurrentHashMap<UUID, List<ToolExecutionAudit>> auditsByTurnId =
      new ConcurrentHashMap<>();

  public ToolUsageAuditService(
      MeterRegistry meterRegistry,
      ObservationRegistry observationRegistry,
      ObjectMapper objectMapper,
      TutorAiProperties tutorAiProperties) {
    this.meterRegistry = meterRegistry;
    this.observationRegistry = observationRegistry;
    this.objectMapper = objectMapper;
    this.tutorAiProperties = tutorAiProperties;
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
      var returnPayload = captureReturnPayload(result.value());
      var audit =
          new ToolExecutionAudit(
              ids.conversationId(),
              ids.clientId(),
              ids.turnId(),
              toolName,
              "success",
              nanosToMillis(startedAt),
              inputSummary,
              result.outputSummary(),
              returnPayload.json(),
              returnPayload.preview(),
              returnPayload.captured(),
              true,
              result.learningSignal().usefulForProfile(),
              ids.profileSnapshotVersion(),
              null);
      register(audit);
      meterRegistry
          .counter("tool.calls.total", "tool.name", toolName, "tool.status", "success")
          .increment();
      meterRegistry.counter("tool.calls.success", "tool.name", toolName).increment();
      if (result.learningSignal().usefulForProfile()) {
        meterRegistry.counter("tool.profile_signal.total", "tool.name", toolName).increment();
        meterRegistry.counter("tool.profile_signal.useful", "tool.name", toolName).increment();
      }
      Timer.builder("tool.latency")
          .tag("tool.name", toolName)
          .register(meterRegistry)
          .record(audit.latencyMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
      log.info(
          """
          tool_execution tool.name={} tool.status={} tool.latency_ms={} client_id={}\
           conversation_id={} turn_id={} model_requested_tool={} profile_snapshot_version={}\
           input_summary={} output_summary={} payload_captured={} tool_return_preview={}\
           useful_for_profile={} failure_code={}\
          """,
          audit.toolName(),
          audit.status(),
          audit.latencyMs(),
          audit.clientId(),
          audit.conversationId(),
          audit.turnId(),
          audit.modelRequested(),
          audit.profileSnapshotVersion(),
          audit.inputSummary(),
          audit.outputSummary(),
          audit.payloadCaptured(),
          audit.toolReturnPreview(),
          audit.usefulForProfile(),
          audit.failureCode());
      return result.value();
    } catch (RuntimeException exception) {
      var audit =
          new ToolExecutionAudit(
              ids.conversationId(),
              ids.clientId(),
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
              false,
              ids.profileSnapshotVersion(),
              exception.getClass().getSimpleName());
      register(audit);
      meterRegistry
          .counter("tool.calls.total", "tool.name", toolName, "tool.status", "failure")
          .increment();
      meterRegistry.counter("tool.calls.failure", "tool.name", toolName).increment();
      Timer.builder("tool.latency")
          .tag("tool.name", toolName)
          .register(meterRegistry)
          .record(audit.latencyMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
      log.warn(
          "tool_execution tool.name={} tool.status={} tool.latency_ms={} client_id={}"
              + " conversation_id={} turn_id={} model_requested_tool={} profile_snapshot_version={}"
              + " input_summary={} output_summary={} payload_captured={} tool_return_preview={}"
              + " useful_for_profile={} failure_code={}",
          audit.toolName(),
          audit.status(),
          audit.latencyMs(),
          audit.clientId(),
          audit.conversationId(),
          audit.turnId(),
          audit.modelRequested(),
          audit.profileSnapshotVersion(),
          audit.inputSummary(),
          audit.outputSummary(),
          audit.payloadCaptured(),
          audit.toolReturnPreview(),
          audit.usefulForProfile(),
          audit.failureCode());
      throw exception;
    } finally {
      observation.stop();
    }
  }

  public List<ToolExecutionAudit> drainTurnAudits(UUID turnId) {
    var audits = auditsByTurnId.remove(turnId);
    return audits == null ? List.of() : List.copyOf(audits);
  }

  private void register(ToolExecutionAudit audit) {
    auditsByTurnId.compute(
        audit.turnId(),
        (_, existing) -> {
          var next =
              existing == null ? new ArrayList<ToolExecutionAudit>() : new ArrayList<>(existing);
          next.add(audit);
          return next;
        });
  }

  private long nanosToMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }

  private ToolReturnPayload captureReturnPayload(Object value) {
    var observability = tutorAiProperties.getToolObservability();
    if (observability == null || !observability.isCapturePayloads()) {
      return ToolReturnPayload.disabled();
    }
    try {
      var json = objectMapper.writeValueAsString(value);
      return new ToolReturnPayload(true, json, preview(json, observability.getMaxPayloadChars()));
    } catch (JacksonException ex) {
      return new ToolReturnPayload(
          true, null, "serialization_error=" + ex.getClass().getSimpleName());
    }
  }

  private String preview(String json, int maxPayloadChars) {
    var maxLength = Math.max(0, maxPayloadChars);
    var oneLine = json.replaceAll("\\s+", " ");
    return oneLine.length() <= maxLength ? oneLine : oneLine.substring(0, maxLength);
  }

  private ToolInvocationIds ids(ToolContext toolContext) {
    var context = toolContext.getContext();
    return new ToolInvocationIds(
        UUID.fromString(String.valueOf(context.get(CLIENT_ID))),
        UUID.fromString(String.valueOf(context.get(CONVERSATION_ID))),
        UUID.fromString(String.valueOf(context.get(TURN_ID))),
        Long.parseLong(String.valueOf(context.getOrDefault(PROFILE_VERSION, 0L))));
  }

  public record ToolResult<T>(T value, String outputSummary, ToolLearningSignal learningSignal) {}

  private record ToolReturnPayload(boolean captured, String json, String preview) {

    static ToolReturnPayload disabled() {
      return new ToolReturnPayload(false, null, null);
    }
  }

  private record ToolInvocationIds(
      UUID clientId, UUID conversationId, UUID turnId, long profileSnapshotVersion) {}
}
