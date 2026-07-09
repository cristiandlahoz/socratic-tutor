package com.wornux.infrastructure.external.crunner;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.wornux.config.ApplicationProperties;
import com.wornux.services.crunner.CDiagnostic;
import com.wornux.services.crunner.CDiagnosticSeverity;
import com.wornux.services.crunner.CSourceRequest;
import com.wornux.services.crunner.CValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DockerGccCCompilerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DockerGccCCompilerAdapter.class);

    private final ApplicationProperties.CRunner properties;
    private final SarifDiagnosticParser sarifDiagnosticParser;
    private final CWorkspaceFactory workspaceFactory;
    private final CCompilerRunner compilerRunner;

    public DockerGccCCompilerAdapter(
            ApplicationProperties.CRunner properties,
            SarifDiagnosticParser sarifDiagnosticParser,
            CWorkspaceFactory workspaceFactory,
            CCompilerRunner compilerRunner) {
        this.properties = properties;
        this.sarifDiagnosticParser = sarifDiagnosticParser;
        this.workspaceFactory = workspaceFactory;
        this.compilerRunner = compilerRunner;
    }

    public String cacheKey() {
        return "docker-gcc:%s:%s:%s:%s".formatted(
            properties.getCompilerImage(),
            properties.getMemory(),
            properties.getCpus(),
            properties.getPidsLimit());
    }

    public CValidationResult validateSyntax(CSourceRequest request, String sourceHash) {
        var startedAt = System.nanoTime();
        try (var workspace = workspaceFactory.compilerWorkspace(request)) {
            var processResult = compilerRunner.validate(workspace, request);
            var elapsedMs = elapsedMillis(startedAt);
            if (processResult.timedOut()) {
                return failure(
                    "C compiler timed out after %d seconds".formatted(properties.getTimeout().toSeconds()),
                    "compiler-timeout",
                    elapsedMs,
                    sourceHash);
            }

            var diagnostics = parseDiagnostics(processResult.stderr(), request.source());
            if (diagnostics.isEmpty() && processResult.exitCode() != 0) {
                diagnostics = List.of(
                    CDiagnostic.error(
                        "C compiler failed before producing diagnostics: %s"
                                .formatted(preview(processResult.stderr(), processResult.stdout())),
                        "compiler-failed"));
            }
            var valid = processResult.exitCode() == 0
                    && diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == CDiagnosticSeverity.ERROR);
            return new CValidationResult(valid, diagnostics, properties.getCompilerImage(), elapsedMs, sourceHash);
        }
        catch (IOException | RuntimeException ex) {
            log.warn("Failed to run sandboxed GCC syntax validation", ex);
            return failure(
                "C compiler sandbox is unavailable: %s".formatted(ex.getMessage()),
                "compiler-unavailable",
                elapsedMillis(startedAt),
                sourceHash);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failure(
                "C compiler sandbox was interrupted",
                "compiler-interrupted",
                elapsedMillis(startedAt),
                sourceHash);
        }
    }

    private List<CDiagnostic> parseDiagnostics(String stderr, String source) {
        try {
            return sarifDiagnosticParser.parse(stderr, source);
        }
        catch (RuntimeException ex) {
            log.warn("Failed to parse GCC SARIF diagnostics", ex);
            return List.of(
                CDiagnostic.error(
                    "Unable to parse compiler diagnostics: %s".formatted(preview(stderr, "")),
                    "diagnostic-parse-failed"));
        }
    }

    private CValidationResult failure(String message, String ruleId, long elapsedMs, String sourceHash) {
        return new CValidationResult(false,
                List.of(CDiagnostic.error(message, ruleId)),
                properties.getCompilerImage(),
                elapsedMs,
                sourceHash);
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static String preview(String stderr, String stdout) {
        var combined = "%s\n%s".formatted(stderr == null ? "" : stderr, stdout == null ? "" : stdout).trim();
        if (combined.isBlank()) {
            return "no compiler output";
        }
        return combined.length() <= 500 ? combined : combined.substring(0, 500) + "...";
    }
}
