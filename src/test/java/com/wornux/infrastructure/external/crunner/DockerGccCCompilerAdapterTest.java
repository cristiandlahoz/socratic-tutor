package com.wornux.infrastructure.external.crunner;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.application.crunner.CDiagnosticSeverity;
import com.wornux.application.crunner.CProgramAnalysisProperties;
import com.wornux.application.crunner.CSourceRequest;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DockerGccCCompilerAdapterTest {

  @Test
  void commandUsesSandboxPolicy() {
    var properties = properties();
    var adapter = adapter(properties);

    var command =
        adapter.compilerCommand(
            java.nio.file.Path.of("/tmp/c-runner-test"),
            new CSourceRequest("int main(void) { return 0; }", null, null));

    assertThat(command).containsSubsequence("--network", "none");
    assertThat(command).containsSubsequence("--memory", properties.getMemory());
    assertThat(command).containsSubsequence("--cpus", properties.getCpus());
    assertThat(command).containsSubsequence("--pids-limit", String.valueOf(properties.getPidsLimit()));
    assertThat(command).contains("--read-only");
    assertThat(command).contains("-fdiagnostics-format=sarif-stderr");
  }

  @Test
  void validatesValidCSourceWithRealGcc() {
    assumeDockerImageIsAvailable();

    var result =
        adapter(properties())
            .validateSyntax(
                new CSourceRequest("int main(void) { return 0; }\n", null, null), "hash");

    assertThat(result.valid()).isTrue();
    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.compiler()).isEqualTo(properties().getCompilerImage());
  }

  @Test
  void returnsStructuredErrorForInvalidCSource() {
    assumeDockerImageIsAvailable();

    var result =
        adapter(properties()).validateSyntax(new CSourceRequest("int main( { return 0; }\n", null, null), "hash");

    assertThat(result.valid()).isFalse();
    assertThat(result.diagnostics()).anySatisfy(
        diagnostic -> {
          assertThat(diagnostic.severity()).isEqualTo(CDiagnosticSeverity.ERROR);
          assertThat(diagnostic.line()).isNotNull();
          assertThat(diagnostic.column()).isNotNull();
        });
  }

  @Test
  void warningOnlySourceStillCountsAsValid() {
    assumeDockerImageIsAvailable();

    var result =
        adapter(properties())
            .validateSyntax(
                new CSourceRequest("int main(void) { int unused = 1; return 0; }\n", null, null),
                "hash");

    assertThat(result.valid()).isTrue();
    assertThat(result.diagnostics())
        .anySatisfy(diagnostic -> assertThat(diagnostic.severity()).isEqualTo(CDiagnosticSeverity.WARNING));
  }

  @Test
  void returnsTimeoutDiagnosticWhenCompilerExceedsLimit() {
    assumeDockerImageIsAvailable();
    var properties = properties();
    properties.setTimeout(Duration.ofMillis(1));

    var result =
        adapter(properties)
            .validateSyntax(
                new CSourceRequest("int main(void) { return 0; }\n", null, null), "hash");

    assertThat(result.valid()).isFalse();
    assertThat(result.diagnostics()).extracting("ruleId").contains("compiler-timeout");
  }

  private static DockerGccCCompilerAdapter adapter(CProgramAnalysisProperties properties) {
    return new DockerGccCCompilerAdapter(
        properties, new SarifDiagnosticParser(new ObjectMapper()));
  }

  private static CProgramAnalysisProperties properties() {
    return new CProgramAnalysisProperties();
  }

  private static void assumeDockerImageIsAvailable() {
    var image = properties().getCompilerImage();
    try {
      var process =
          new ProcessBuilder("docker", "image", "inspect", image)
              .redirectErrorStream(true)
              .start();
      Assumptions.assumeTrue(process.waitFor() == 0, "Docker image %s is not local".formatted(image));
    } catch (IOException exception) {
      Assumptions.assumeTrue(false, "Docker is unavailable: " + exception.getMessage());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      Assumptions.assumeTrue(false, "Docker image inspection was interrupted");
    }
  }
}
