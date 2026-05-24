package com.wornux.infrastructure.external.crunner;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.wornux.application.crunner.CDiagnostic;
import com.wornux.application.crunner.CDiagnosticSeverity;
import com.wornux.application.crunner.CProgramAnalysisProperties;
import com.wornux.application.crunner.CSourceRequest;
import com.wornux.application.crunner.CValidationResult;
import com.wornux.application.crunner.port.CCompilerPort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DockerGccCCompilerAdapter implements CCompilerPort {

  private static final Logger log = LoggerFactory.getLogger(DockerGccCCompilerAdapter.class);
  private static final String WORKSPACE = "/workspace";

  private final CProgramAnalysisProperties properties;
  private final SarifDiagnosticParser sarifDiagnosticParser;

  public DockerGccCCompilerAdapter(
      CProgramAnalysisProperties properties, SarifDiagnosticParser sarifDiagnosticParser) {
    this.properties = properties;
    this.sarifDiagnosticParser = sarifDiagnosticParser;
  }

  @Override
  public String cacheKey() {
    return "docker-gcc:%s:%s:%s:%s"
        .formatted(
            properties.getCompilerImage(),
            properties.getMemory(),
            properties.getCpus(),
            properties.getPidsLimit());
  }

  @Override
  public CValidationResult validateSyntax(CSourceRequest request, String sourceHash) {
    var startedAt = System.nanoTime();
    Path tempDir = null;
    try {
      tempDir = Files.createTempDirectory("c-runner-");
      writeSourceFile(tempDir, request);
      var processResult = runCompiler(tempDir, request);
      var elapsedMs = elapsedMillis(startedAt);
      if (processResult.timedOut()) {
        return failure(
            "C compiler timed out after " + properties.getTimeout().toSeconds() + " seconds",
            "compiler-timeout",
            elapsedMs,
            sourceHash);
      }

      var diagnostics = parseDiagnostics(processResult.stderr(), request.source());
      if (diagnostics.isEmpty() && processResult.exitCode() != 0) {
        diagnostics =
            List.of(
                CDiagnostic.error(
                    "C compiler failed before producing diagnostics: "
                        + preview(processResult.stderr(), processResult.stdout()),
                    "compiler-failed"));
      }
      var valid =
          processResult.exitCode() == 0
              && diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == CDiagnosticSeverity.ERROR);
      return new CValidationResult(valid, diagnostics, properties.getCompilerImage(), elapsedMs, sourceHash);
    } catch (IOException exception) {
      log.warn("Failed to run sandboxed GCC syntax validation", exception);
      return failure(
          "C compiler sandbox is unavailable: " + exception.getMessage(),
          "compiler-unavailable",
          elapsedMillis(startedAt),
          sourceHash);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return failure(
          "C compiler sandbox was interrupted",
          "compiler-interrupted",
          elapsedMillis(startedAt),
          sourceHash);
    } finally {
      deleteRecursively(tempDir);
    }
  }

  private void writeSourceFile(Path tempDir, CSourceRequest request) throws IOException {
    var sourcePath = tempDir.resolve(request.filename()).normalize();
    if (!sourcePath.getParent().equals(tempDir)) {
      throw new IOException("Unsafe C source filename: " + request.filename());
    }
    Files.writeString(sourcePath, request.source(), UTF_8, StandardOpenOption.CREATE_NEW);
  }

  private CompilerProcessResult runCompiler(Path tempDir, CSourceRequest request)
      throws IOException, InterruptedException {
    var command = compilerCommand(tempDir, request);
    var process = new ProcessBuilder(command).start();
    var stdout = readAsync(process.getInputStream());
    var stderr = readAsync(process.getErrorStream());
    var finished = process.waitFor(timeoutMillis(properties.getTimeout()), TimeUnit.MILLISECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(2, TimeUnit.SECONDS);
      return new CompilerProcessResult(-1, awaitOutput(stdout), awaitOutput(stderr), true);
    }
    return new CompilerProcessResult(process.exitValue(), awaitOutput(stdout), awaitOutput(stderr), false);
  }

  List<String> compilerCommand(Path tempDir, CSourceRequest request) {
    return List.of(
        "docker",
        "run",
        "--rm",
        "--network",
        "none",
        "--cpus",
        properties.getCpus(),
        "--memory",
        properties.getMemory(),
        "--pids-limit",
        String.valueOf(properties.getPidsLimit()),
        "--read-only",
        "--tmpfs",
        "/tmp:rw,noexec,nosuid,size=16m",
        "-v",
        tempDir.toAbsolutePath() + ":" + WORKSPACE + ":ro",
        "-w",
        WORKSPACE,
        properties.getCompilerImage(),
        "gcc",
        "-fsyntax-only",
        "-std=" + request.standard(),
        "-Wall",
        "-Wextra",
        "-Wpedantic",
        "-fdiagnostics-format=sarif-stderr",
        request.filename());
  }

  private List<CDiagnostic> parseDiagnostics(String stderr, String source) {
    try {
      return sarifDiagnosticParser.parse(stderr, source);
    } catch (RuntimeException exception) {
      log.warn("Failed to parse GCC SARIF diagnostics", exception);
      return List.of(
          CDiagnostic.error(
              "Unable to parse compiler diagnostics: " + preview(stderr, ""), "diagnostic-parse-failed"));
    }
  }

  private CValidationResult failure(
      String message, String ruleId, long elapsedMs, String sourceHash) {
    return new CValidationResult(
        false,
        List.of(CDiagnostic.error(message, ruleId)),
        properties.getCompilerImage(),
        elapsedMs,
        sourceHash);
  }

  private static CompletableFuture<String> readAsync(InputStream stream) {
    return CompletableFuture.supplyAsync(
        () -> {
          try (stream) {
            return new String(stream.readAllBytes(), UTF_8);
          } catch (IOException exception) {
            throw new CompletionException(exception);
          }
        });
  }

  private static String awaitOutput(CompletableFuture<String> output) {
    try {
      return output.join();
    } catch (CompletionException exception) {
      return "";
    }
  }

  private static long timeoutMillis(Duration timeout) {
    return Math.max(1, timeout == null ? Duration.ofSeconds(8).toMillis() : timeout.toMillis());
  }

  private static long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  private static String preview(String stderr, String stdout) {
    var combined = ((stderr == null ? "" : stderr) + "\n" + (stdout == null ? "" : stdout)).trim();
    if (combined.isBlank()) {
      return "no compiler output";
    }
    return combined.length() <= 500 ? combined : combined.substring(0, 500) + "...";
  }

  private static void deleteRecursively(Path path) {
    if (path == null || !Files.exists(path)) {
      return;
    }
    try (var paths = Files.walk(path)) {
      paths.sorted(Comparator.reverseOrder())
          .forEach(
              currentPath -> {
                try {
                  Files.deleteIfExists(currentPath);
                } catch (IOException exception) {
                  log.debug("Failed to delete temporary C runner path {}", currentPath, exception);
                }
              });
    } catch (IOException exception) {
      log.debug("Failed to clean temporary C runner directory {}", path, exception);
    }
  }

  private record CompilerProcessResult(int exitCode, String stdout, String stderr, boolean timedOut) {}
}
