package com.wornux.presentation.crunner;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.application.crunner.CDiagnostic;
import com.wornux.application.crunner.CDiagnosticSeverity;
import com.wornux.application.crunner.CProgramAnalysisProperties;
import com.wornux.application.crunner.CProgramAnalysisService;
import com.wornux.application.crunner.CSourceRequest;
import com.wornux.application.crunner.CValidationResult;
import com.wornux.application.crunner.port.CCompilerPort;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CRunnerTestPanelTest {

  @Test
  void rendersCodeVisualizerScaffold() {
    var panel =
        new CRunnerTestPanel(new CProgramAnalysisService(new FakeCompiler(), new CProgramAnalysisProperties()));

    var visibleText = componentText(panel);

    assertThat(visibleText)
        .contains("Code Visualizer", "State", "247 vars", "Locals (3)", "int", "value", "42");
  }

  @Test
  void validationUpdatesStatusWithCompilerResult() {
    var compiler = new FakeCompiler();
    var panel =
        new CRunnerTestPanel(new CProgramAnalysisService(compiler, new CProgramAnalysisProperties()));

    panel.setSourceForTesting("int main(void) { int unused = 1; return 0; }");
    panel.validateCurrentSource();

    assertThat(panel.statusTextForTesting()).contains("Valido", "1 warning(s)", "fake-gcc");
    assertThat(compiler.lastRequest.source()).contains("unused");
  }

  @Test
  void stepAndResetUpdateActiveLineState() {
    var panel =
        new CRunnerTestPanel(new CProgramAnalysisService(new FakeCompiler(), new CProgramAnalysisProperties()));

    assertThat(panel.activeLineForTesting()).isEqualTo(5);

    panel.stepActiveLine();
    assertThat(panel.activeLineForTesting()).isEqualTo(6);

    panel.stepActiveLine();
    assertThat(panel.activeLineForTesting()).isEqualTo(4);

    panel.resetActiveLine();
    assertThat(panel.activeLineForTesting()).isEqualTo(4);
  }

  private static List<String> componentText(com.vaadin.flow.component.Component component) {
    return Stream.concat(
            Stream.of(component.getElement().getText()),
            component.getChildren().flatMap(CRunnerTestPanelTest::componentTextStream))
        .filter(text -> text != null && !text.isBlank())
        .toList();
  }

  private static Stream<String> componentTextStream(com.vaadin.flow.component.Component component) {
    return Stream.concat(
        Stream.of(component.getElement().getText()),
        component.getChildren().flatMap(CRunnerTestPanelTest::componentTextStream));
  }

  private static final class FakeCompiler implements CCompilerPort {

    private CSourceRequest lastRequest;

    @Override
    public String cacheKey() {
      return "fake";
    }

    @Override
    public CValidationResult validateSyntax(CSourceRequest request, String sourceHash) {
      lastRequest = request;
      var diagnostics =
          List.of(
              new CDiagnostic(
                  CDiagnosticSeverity.WARNING,
                  "unused variable",
                  1,
                  22,
                  1,
                  28,
                  21,
                  27,
                  "-Wunused-variable"));
      return new CValidationResult(true, diagnostics, "fake-gcc", 7, sourceHash);
    }
  }
}
