package com.wornux.crunner;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.application.crunner.CDiagnostic;
import com.wornux.application.crunner.CProgramAnalysisProperties;
import com.wornux.application.crunner.CProgramAnalysisService;
import com.wornux.application.crunner.CSourceRequest;
import com.wornux.application.crunner.CValidationResult;
import com.wornux.application.crunner.port.CCompilerPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CProgramAnalysisServiceTest {

  @Test
  void rejectsOversizedSourceBeforeCallingCompiler() {
    var properties = new CProgramAnalysisProperties();
    properties.setMaxSourceBytes(4);
    var compiler = new FakeCompiler();
    var service = new CProgramAnalysisService(compiler, properties);

    var result = service.validateSyntax(new CSourceRequest("too large", null, null));

    assertThat(result.valid()).isFalse();
    assertThat(result.compiler()).isEqualTo("not-run");
    assertThat(result.diagnostics()).extracting(CDiagnostic::ruleId).containsExactly("source-too-large");
    assertThat(compiler.calls()).isZero();
  }

  @Test
  void rejectsUnsupportedStandardBeforeCallingCompiler() {
    var compiler = new FakeCompiler();
    var service = new CProgramAnalysisService(compiler, new CProgramAnalysisProperties());

    var result = service.validateSyntax(new CSourceRequest("int main(void) { return 0; }", "c99", null));

    assertThat(result.valid()).isFalse();
    assertThat(result.diagnostics()).extracting(CDiagnostic::ruleId).containsExactly("unsupported-standard");
    assertThat(compiler.calls()).isZero();
  }

  @Test
  void cachesBySourceAndCompilerConfiguration() {
    var compiler = new FakeCompiler();
    var service = new CProgramAnalysisService(compiler, new CProgramAnalysisProperties());
    var request = new CSourceRequest("int main(void) { return 0; }", null, null);

    var first = service.validateSyntax(request);
    var second = service.validateSyntax(request);

    assertThat(first).isSameAs(second);
    assertThat(first.valid()).isTrue();
    assertThat(compiler.calls()).isEqualTo(1);
  }

  private static final class FakeCompiler implements CCompilerPort {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public String cacheKey() {
      return "fake";
    }

    @Override
    public CValidationResult validateSyntax(CSourceRequest request, String sourceHash) {
      calls.incrementAndGet();
      return new CValidationResult(true, List.of(), "fake-gcc", 1, sourceHash);
    }

    int calls() {
      return calls.get();
    }
  }
}
