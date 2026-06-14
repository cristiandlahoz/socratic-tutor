package com.wornux.infrastructure.external.crunner;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.wornux.config.CProgramAnalysisProperties;
import com.wornux.services.crunner.CDebugRequest;
import com.wornux.services.crunner.CDebugSessionResult;
import com.wornux.services.crunner.CDiagnostic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DockerGdbCDebuggerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DockerGdbCDebuggerAdapter.class);

    private final CProgramAnalysisProperties properties;
    private final SarifDiagnosticParser sarifDiagnosticParser;
    private final GdbDebugDumpParser debugDumpParser;
    private final CWorkspaceFactory workspaceFactory;
    private final CDebuggerRunner debuggerRunner;

    public DockerGdbCDebuggerAdapter(
            CProgramAnalysisProperties properties,
            SarifDiagnosticParser sarifDiagnosticParser,
            GdbDebugDumpParser debugDumpParser,
            CWorkspaceFactory workspaceFactory,
            CDebuggerRunner debuggerRunner) {
        this.properties = properties;
        this.sarifDiagnosticParser = sarifDiagnosticParser;
        this.debugDumpParser = debugDumpParser;
        this.workspaceFactory = workspaceFactory;
        this.debuggerRunner = debuggerRunner;
    }

    public String cacheKey() {
        return "docker-gdb:%s:%s:%s:%s:%d".formatted(
            properties.getDebuggerImage(),
            properties.getDebuggerMemory(),
            properties.getCpus(),
            properties.getPidsLimit(),
            properties.getMaxSnapshots());
    }

    public CDebugSessionResult debug(CDebugRequest request, String sourceHash) {
        var startedAt = System.nanoTime();
        try (var workspace = workspaceFactory.debuggerWorkspace(request, properties.getMaxSnapshots())) {
            var processResult = debuggerRunner.debug(workspace, request);
            var elapsedMs = elapsedMillis(startedAt);
            if (processResult.timedOut()) {
                return failure("C debugger timed out", "debugger-timeout", elapsedMs, sourceHash);
            }
            if (processResult.exitCode() == 127 || processResult.stderr().contains("gdb not found")) {
                return failure(
                    "Debugger image does not contain gdb: %s".formatted(properties.getDebuggerImage()),
                    "gdb-unavailable",
                    elapsedMs,
                    sourceHash);
            }
            if (processResult.exitCode() != 0 && processResult.stdout().isBlank()) {
                var diagnostics = parseCompilerDiagnostics(processResult.stderr(), request.source());
                if (diagnostics.isEmpty()) {
                    diagnostics = List.of(
                        CDiagnostic.error(
                            "Debugger failed: %s".formatted(preview(processResult.stderr())),
                            "debugger-failed"));
                }
                return new CDebugSessionResult(false,
                        diagnostics,
                        List.of(),
                        properties.getDebuggerImage(),
                        elapsedMs,
                        sourceHash);
            }

            var snapshots = debugDumpParser
                    .parse(processResult.stdout(), properties.getMaxSnapshots(), properties.getMaxOutputBytes());
            if (snapshots.isEmpty()) {
                return failure(
                    "Debugger produced no snapshots: %s"
                            .formatted(preview("%s%n%s".formatted(processResult.stderr(), processResult.stdout()))),
                    "debugger-empty",
                    elapsedMs,
                    sourceHash);
            }
            return new CDebugSessionResult(true,
                    List.of(),
                    snapshots,
                    properties.getDebuggerImage(),
                    elapsedMs,
                    sourceHash);
        }
        catch (IOException | RuntimeException ex) {
            log.warn("Failed to run sandboxed GDB debugger", ex);
            return failure(
                "C debugger sandbox is unavailable: %s".formatted(ex.getMessage()),
                "debugger-unavailable",
                elapsedMillis(startedAt),
                sourceHash);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failure(
                "C debugger sandbox was interrupted",
                "debugger-interrupted",
                elapsedMillis(startedAt),
                sourceHash);
        }
    }

    private List<CDiagnostic> parseCompilerDiagnostics(String stderr, String source) {
        try {
            return sarifDiagnosticParser.parse(stderr, source);
        }
        catch (RuntimeException ex) {
            return List.of();
        }
    }

    private CDebugSessionResult failure(String message, String ruleId, long elapsedMs, String sourceHash) {
        return new CDebugSessionResult(false,
                List.of(CDiagnostic.error(message, ruleId)),
                List.of(),
                properties.getDebuggerImage(),
                elapsedMs,
                sourceHash);
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static String preview(String value) {
        var safe = value == null ? "" : value.trim();
        if (safe.isBlank()) {
            return "no debugger output";
        }
        return safe.length() <= 500 ? safe : safe.substring(0, 500) + "...";
    }

}
