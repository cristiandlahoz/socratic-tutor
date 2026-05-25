package com.wornux.infrastructure.external.crunner;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class DockerCommandRunner {

  CommandResult run(List<String> command, Duration timeout) throws IOException, InterruptedException {
    var process = new ProcessBuilder(command).start();
    var stdout = readAsync(process.getInputStream());
    var stderr = readAsync(process.getErrorStream());
    var finished = process.waitFor(timeoutMillis(timeout), TimeUnit.MILLISECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(2, TimeUnit.SECONDS);
      return new CommandResult(-1, awaitOutput(stdout), awaitOutput(stderr), true);
    }
    return new CommandResult(process.exitValue(), awaitOutput(stdout), awaitOutput(stderr), false);
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

  static final class CommandResult {

    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean timedOut;

    CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {
      this.exitCode = exitCode;
      this.stdout = stdout == null ? "" : stdout;
      this.stderr = stderr == null ? "" : stderr;
      this.timedOut = timedOut;
    }

    int exitCode() {
      return exitCode;
    }

    String stdout() {
      return stdout;
    }

    String stderr() {
      return stderr;
    }

    boolean timedOut() {
      return timedOut;
    }
  }
}
