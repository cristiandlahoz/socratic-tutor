package com.wornux.infrastructure.external.crunner;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.wornux.services.crunner.CDebugRequest;
import com.wornux.services.crunner.CDebugSessionResult;
import com.wornux.services.crunner.CDiagnostic;
import com.wornux.config.CProgramAnalysisProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DockerGdbCDebuggerAdapter {

  private static final Logger log = LoggerFactory.getLogger(DockerGdbCDebuggerAdapter.class);
  private static final String WORKSPACE = "/workspace";

  private final CProgramAnalysisProperties properties;
  private final SarifDiagnosticParser sarifDiagnosticParser;
  private final GdbMiSnapshotParser snapshotParser;
  private final DockerCommandRunner commandRunner;

  public DockerGdbCDebuggerAdapter(
      CProgramAnalysisProperties properties,
      SarifDiagnosticParser sarifDiagnosticParser,
      GdbMiSnapshotParser snapshotParser,
      DockerCommandRunner commandRunner) {
    this.properties = properties;
    this.sarifDiagnosticParser = sarifDiagnosticParser;
    this.snapshotParser = snapshotParser;
    this.commandRunner = commandRunner;
  }

  public String cacheKey() {
    return "docker-gdb:%s:%s:%s:%s:%d"
        .formatted(
            properties.getDebuggerImage(),
            properties.getDebuggerMemory(),
            properties.getCpus(),
            properties.getPidsLimit(),
            properties.getMaxSnapshots());
  }

  public CDebugSessionResult debug(CDebugRequest request, String sourceHash) {
    var startedAt = System.nanoTime();
    Path tempDir = null;
    try {
      tempDir = Files.createTempDirectory("c-debugger-");
      writeSourceFile(tempDir, request);
      writeStdinFile(tempDir, request);
      writeStdoutFile(tempDir);
      writeDebuggerScript(tempDir);
      var processResult = commandRunner.run(debuggerCommand(tempDir, request), properties.getDebugTimeout());
      var elapsedMs = elapsedMillis(startedAt);
      if (processResult.timedOut()) {
        return failure("C debugger timed out", "debugger-timeout", elapsedMs, sourceHash);
      }
      if (processResult.exitCode() == 127 || processResult.stderr().contains("gdb not found")) {
        return failure(
            "Debugger image does not contain gdb: " + properties.getDebuggerImage(),
            "gdb-unavailable",
            elapsedMs,
            sourceHash);
      }
      if (processResult.exitCode() != 0 && processResult.stdout().isBlank()) {
        var diagnostics = parseCompilerDiagnostics(processResult.stderr(), request.source());
        if (diagnostics.isEmpty()) {
          diagnostics =
              List.of(CDiagnostic.error("Debugger failed: " + preview(processResult.stderr()), "debugger-failed"));
        }
        return new CDebugSessionResult(
            false, diagnostics, List.of(), properties.getDebuggerImage(), elapsedMs, sourceHash);
      }

      var snapshots =
          snapshotParser.parse(
              processResult.stdout(), properties.getMaxSnapshots(), properties.getMaxOutputBytes());
      if (snapshots.isEmpty()) {
        return failure(
            "Debugger produced no snapshots: " + preview(processResult.stderr() + "\n" + processResult.stdout()),
            "debugger-empty",
            elapsedMs,
            sourceHash);
      }
      var capped =
          snapshots.size() >= properties.getMaxSnapshots()
              ? snapshots
              : snapshots;
      return new CDebugSessionResult(
          true, List.of(), capped, properties.getDebuggerImage(), elapsedMs, sourceHash);
    } catch (IOException | RuntimeException exception) {
      log.warn("Failed to run sandboxed GDB debugger", exception);
      return failure(
          "C debugger sandbox is unavailable: " + exception.getMessage(),
          "debugger-unavailable",
          elapsedMillis(startedAt),
          sourceHash);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return failure(
          "C debugger sandbox was interrupted",
          "debugger-interrupted",
          elapsedMillis(startedAt),
          sourceHash);
    } finally {
      deleteRecursively(tempDir);
    }
  }

  private void writeSourceFile(Path tempDir, CDebugRequest request) throws IOException {
    var sourcePath = tempDir.resolve(request.filename()).normalize();
    if (!sourcePath.getParent().equals(tempDir)) {
      throw new IOException("Unsafe C source filename: " + request.filename());
    }
    Files.writeString(sourcePath, request.source(), UTF_8, StandardOpenOption.CREATE_NEW);
  }

  private void writeStdinFile(Path tempDir, CDebugRequest request) throws IOException {
    Files.writeString(tempDir.resolve("stdin.txt"), request.stdin(), UTF_8, StandardOpenOption.CREATE_NEW);
  }

  private void writeStdoutFile(Path tempDir) throws IOException {
    Files.writeString(tempDir.resolve("stdout.txt"), "", UTF_8, StandardOpenOption.CREATE_NEW);
  }

  private void writeDebuggerScript(Path tempDir) throws IOException {
    var commands = new StringBuilder();
    commands.append("-gdb-set pagination off\n");
    commands.append("-gdb-set print elements 64\n");
    commands.append("-file-exec-and-symbols ").append(WORKSPACE).append("/main\n");
    commands.append("-interpreter-exec console \"break main\"\n");
    commands
        .append("-interpreter-exec console \"run < ")
        .append(WORKSPACE)
        .append("/stdin.txt > ")
        .append(WORKSPACE)
        .append("/stdout.txt\"\n");
    for (int i = 0; i < properties.getMaxSnapshots(); i++) {
      commands.append("-stack-list-frames\n");
      commands.append("-interpreter-exec console \"call (int) fflush(0)\"\n");
      commands
          .append("-interpreter-exec console \"shell printf '__C_STDOUT_BEGIN__\\\\n'; cat ")
          .append(WORKSPACE)
          .append("/stdout.txt 2>/dev/null; printf '__C_STDOUT_END__\\\\n'\"\n");
      commands.append("-stack-list-variables --all-values\n");
      commands.append("-interpreter-exec console \"next\"\n");
    }
    commands.append("-gdb-exit\n");
    Files.writeString(tempDir.resolve("debug.mi"), commands.toString(), UTF_8, StandardOpenOption.CREATE_NEW);
  }

  List<String> debuggerCommand(Path tempDir, CDebugRequest request) {
    return List.of(
        "docker",
        "run",
        "--rm",
        "--network",
        "none",
        "--cap-add",
        "SYS_PTRACE",
        "--security-opt",
        "seccomp=unconfined",
        "--cpus",
        properties.getCpus(),
        "--memory",
        properties.getDebuggerMemory(),
        "--pids-limit",
        String.valueOf(properties.getPidsLimit()),
        "--tmpfs",
        "/tmp:rw,nosuid,size=32m",
        "-v",
        tempDir.toAbsolutePath() + ":" + WORKSPACE + ":rw",
        "-w",
        WORKSPACE,
        properties.getDebuggerImage(),
        "sh",
        "-lc",
        "command -v gdb >/dev/null 2>&1 || { echo 'gdb not found' >&2; exit 127; }; "
            + "gcc -std="
            + request.standard()
            + " -Wall -Wextra -Wpedantic -g -O0 -fno-omit-frame-pointer "
            + "-fdiagnostics-format=sarif-stderr "
            + request.filename()
            + " -o main && gdb --quiet --interpreter=mi2 < debug.mi");
  }

  private List<CDiagnostic> parseCompilerDiagnostics(String stderr, String source) {
    try {
      return sarifDiagnosticParser.parse(stderr, source);
    } catch (RuntimeException exception) {
      return List.of();
    }
  }

  private CDebugSessionResult failure(
      String message, String ruleId, long elapsedMs, String sourceHash) {
    return new CDebugSessionResult(
        false,
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
                  log.debug("Failed to delete temporary C debugger path {}", currentPath, exception);
                }
              });
    } catch (IOException exception) {
      log.debug("Failed to clean temporary C debugger directory {}", path, exception);
    }
  }
}
