package com.wornux.crunner;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.application.crunner.CDiagnosticSeverity;
import com.wornux.infrastructure.external.crunner.SarifDiagnosticParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SarifDiagnosticParserTest {

  private final SarifDiagnosticParser parser = new SarifDiagnosticParser(new ObjectMapper());

  @Test
  void mapsGccSarifResultToStructuredDiagnostic() {
    var source = """
        int main(void) {
          return missing;
        }
        """;

    var diagnostics = parser.parse(sarif("error", "'missing' undeclared", 2, 10, "gcc/error"), source);

    assertThat(diagnostics).hasSize(1);
    var diagnostic = diagnostics.getFirst();
    assertThat(diagnostic.severity()).isEqualTo(CDiagnosticSeverity.ERROR);
    assertThat(diagnostic.message()).isEqualTo("'missing' undeclared");
    assertThat(diagnostic.line()).isEqualTo(2);
    assertThat(diagnostic.column()).isEqualTo(10);
    assertThat(diagnostic.fromOffset()).isNotNull();
    assertThat(diagnostic.toOffset()).isGreaterThan(diagnostic.fromOffset());
    assertThat(diagnostic.ruleId()).isEqualTo("gcc/error");
  }

  @Test
  void mapsWarningSeverityWithoutMarkingItAsError() {
    var diagnostics =
        parser.parse(sarif("warning", "unused variable 'x'", 1, 22, "-Wunused-variable"), "int main(void) { int x; }");

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics.getFirst().severity()).isEqualTo(CDiagnosticSeverity.WARNING);
    assertThat(diagnostics.getFirst().ruleId()).isEqualTo("-Wunused-variable");
  }

  private static String sarif(String level, String message, int line, int column, String ruleId) {
    return """
        {
          "version": "2.1.0",
          "runs": [
            {
              "tool": {"driver": {"name": "GNU C Compiler"}},
              "results": [
                {
                  "ruleId": "%s",
                  "level": "%s",
                  "message": {"text": "%s"},
                  "locations": [
                    {
                      "physicalLocation": {
                        "artifactLocation": {"uri": "main.c"},
                        "region": {
                          "startLine": %d,
                          "startColumn": %d,
                          "endLine": %d,
                          "endColumn": %d
                        }
                      }
                    }
                  ]
                }
              ]
            }
          ]
        }
        """
        .formatted(ruleId, level, message, line, column, line, column + 1);
  }
}
