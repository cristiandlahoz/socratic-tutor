package com.wornux.presentation.crunner;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HtmlContainer;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.wornux.application.crunner.CDiagnosticSeverity;
import com.wornux.application.crunner.CProgramAnalysisService;
import com.wornux.application.crunner.CSourceRequest;
import com.wornux.application.crunner.CValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@StyleSheet("styles/c-runner.css")
public final class CRunnerTestPanel extends Composite<Div> implements HasSize {

  private static final int INITIAL_ACTIVE_LINE = 5;
  private static final int FIRST_EXECUTABLE_LINE = 4;
  private static final int[] EXECUTION_LINES = {4, 5, 6};
  private static final String DEFAULT_SOURCE =
      """
      #include <stdio.h>

      int main(void) {
          int value = 42;
          printf("value = %d\\n", value);
          return 0;
      }
      """;

  private final CProgramAnalysisService analysisService;
  private final Button validateButton = createIconButton(VaadinIcon.PLAY, "Validar");
  private final Button stepButton = createIconButton(VaadinIcon.ARROW_RIGHT, "Paso siguiente");
  private final Button resetButton = createIconButton(VaadinIcon.ROTATE_LEFT, "Reiniciar");
  private final Button menuButton = createIconButton(VaadinIcon.ELLIPSIS_V, "Diagnosticos");
  private final Span statusText = new Span("");
  private final CDebugSourceViewer sourceViewer = new CDebugSourceViewer();
  private List<com.wornux.application.crunner.CDiagnostic> currentDiagnostics = List.of();
  private String currentSource = DEFAULT_SOURCE;
  private int activeLine = INITIAL_ACTIVE_LINE;

  public CRunnerTestPanel(CProgramAnalysisService analysisService) {
    this.analysisService = Objects.requireNonNull(analysisService, "analysisService must not be null");

    var title = new H2("Code Visualizer");
    title.addClassName("c-runner-title");

    var header = new HorizontalLayout(title);
    header.setPadding(false);
    header.setSpacing(false);
    header.setWidthFull();
    header.addClassName("c-runner-header");

    statusText.addClassName("c-runner-status-text");

    validateButton.addClassName("c-runner-validate-button");
    validateButton.addClickListener(_ -> validateCurrentSource());
    stepButton.addClickListener(_ -> stepActiveLine());
    resetButton.addClickListener(_ -> resetActiveLine());
    menuButton.addClickListener(_ -> showDiagnosticsSummary());

    var controls = new HorizontalLayout(validateButton, stepButton, resetButton, menuButton);
    controls.setPadding(false);
    controls.setSpacing(false);
    controls.addClassName("c-runner-controls");

    sourceViewer.setValue(DEFAULT_SOURCE);
    sourceViewer.setDiagnostics(List.of());
    sourceViewer.setActiveLine(activeLine);
    sourceViewer.setSizeFull();
    sourceViewer.addClassName("c-runner-source-viewer");

    var viewerShell = new Div(sourceViewer);
    viewerShell.addClassName("c-runner-viewer-shell");

    var codeFrame = new Div(viewerShell);
    codeFrame.addClassName("c-runner-code-frame");

    var root = getContent();
    root.setSizeFull();
    root.addClassName("c-runner-panel");
    root.add(header, createStateCard(), controls, statusText, codeFrame);
  }

  void validateCurrentSource() {
    validateButton.setEnabled(false);
    statusText.setText("Validando...");
    try {
      var result =
          analysisService.validateSyntax(new CSourceRequest(currentSource, "c17", "main.c"));
      renderResult(result);
    } finally {
      validateButton.setEnabled(true);
    }
  }

  void setSourceForTesting(String source) {
    currentSource = source == null ? "" : source;
    sourceViewer.setValue(currentSource);
    sourceViewer.setDiagnostics(List.of());
    currentDiagnostics = List.of();
    statusText.setText("");
  }

  String statusTextForTesting() {
    return statusText.getText();
  }

  int activeLineForTesting() {
    return activeLine;
  }

  void stepActiveLine() {
    activeLine = nextExecutionLine();
    sourceViewer.setActiveLine(activeLine);
  }

  void resetActiveLine() {
    activeLine = FIRST_EXECUTABLE_LINE;
    sourceViewer.setActiveLine(activeLine);
  }

  private void renderResult(CValidationResult result) {
    sourceViewer.setValue(currentSource);
    sourceViewer.setDiagnostics(result.diagnostics());
    sourceViewer.setActiveLine(activeLine);
    currentDiagnostics = new ArrayList<>(result.diagnostics());

    var errors =
        result.diagnostics().stream()
            .filter(diagnostic -> diagnostic.severity() == CDiagnosticSeverity.ERROR)
            .count();
    var warnings =
        result.diagnostics().stream()
            .filter(diagnostic -> diagnostic.severity() == CDiagnosticSeverity.WARNING)
            .count();
    statusText.setText(
        "%s | %d error(es) | %d warning(s) | %s | %d ms"
            .formatted(
                result.valid() ? "Valido" : "Con errores",
                errors,
                warnings,
                result.compiler(),
                result.elapsedMs()));
  }

  private int nextExecutionLine() {
    for (int i = 0; i < EXECUTION_LINES.length; i++) {
      if (EXECUTION_LINES[i] == activeLine) {
        return EXECUTION_LINES[(i + 1) % EXECUTION_LINES.length];
      }
    }
    return FIRST_EXECUTABLE_LINE;
  }

  private void showDiagnosticsSummary() {
    if (currentDiagnostics.isEmpty()) {
      statusText.setText("Sin diagnosticos");
      return;
    }
    var first = currentDiagnostics.getFirst();
    statusText.setText("%s linea %d: %s".formatted(first.severity(), first.line(), first.message()));
  }

  private static Button createIconButton(VaadinIcon icon, String label) {
    var button = new Button(new Icon(icon));
    button.addClassName("c-runner-control-button");
    button.getElement().setAttribute("aria-label", label);
    button.getElement().setAttribute("title", label);
    return button;
  }

  private static VerticalLayout createStateCard() {
    var stateTitle = new Span("State");
    stateTitle.addClassName("c-runner-state-title");

    var variableCount = new Span("247 vars");
    variableCount.addClassName("c-runner-state-pill");

    var stateHeader = new HorizontalLayout(stateTitle, variableCount);
    stateHeader.setPadding(false);
    stateHeader.setSpacing(false);
    stateHeader.setWidthFull();
    stateHeader.expand(stateTitle);
    stateHeader.addClassName("c-runner-state-header");

    var localsLabel = new Span("Locals (3)");
    localsLabel.addClassName("c-runner-group-label");
    var localsGroup = new HorizontalLayout(new Icon(VaadinIcon.ANGLE_DOWN), localsLabel);
    localsGroup.setPadding(false);
    localsGroup.setSpacing(false);
    localsGroup.setWidthFull();
    localsGroup.addClassName("c-runner-state-group");

    var body = new VerticalLayout();
    body.setPadding(false);
    body.setSpacing(false);
    body.setWidthFull();
    body.add(
        localsGroup,
        createVariablesTable(),
        createCollapsedGroup(VaadinIcon.GLOBE, "Globals (0)"),
        createCollapsedGroup(VaadinIcon.EYE, "Watch (0)"),
        createCollapsedGroup(VaadinIcon.ASTERISK, "Constants (12)"));
    body.addClassName("c-runner-state-body");

    var card = new VerticalLayout(stateHeader, body);
    card.setPadding(false);
    card.setSpacing(false);
    card.setWidthFull();
    card.addClassName("c-runner-state-card");
    return card;
  }

  private static HtmlContainer createVariablesTable() {
    var table = new HtmlContainer("table");
    table.addClassName("c-runner-vars-table");

    var thead = new HtmlContainer("thead", createHeaderRow());
    var tbody =
        new HtmlContainer(
            "tbody",
            createVariableRow("int", "value", "42"),
            createVariableRow("int", "i", "0"),
            createVariableRow("int", "j", "0"));
    table.add(thead, tbody);
    return table;
  }

  private static HtmlContainer createHeaderRow() {
    return new HtmlContainer(
        "tr", createCell("th", "Type", "c-runner-col-type"), createCell("th", "Name", ""), createCell("th", "Value", ""));
  }

  private static HtmlContainer createVariableRow(String type, String name, String value) {
    var typeChip = new Span(type);
    typeChip.addClassName("c-runner-type-chip");

    var nameText = new Span(name);
    nameText.addClassName("c-runner-var-name");

    var valueText = new Span(value);
    valueText.addClassName("c-runner-var-value");

    return new HtmlContainer(
        "tr",
        createCell("td", typeChip, "c-runner-col-type"),
        createCell("td", nameText, ""),
        createCell("td", valueText, ""));
  }

  private static HtmlContainer createCell(String tag, String text, String className) {
    var cell = new HtmlContainer(tag);
    cell.setText(text);
    if (!className.isBlank()) {
      cell.addClassName(className);
    }
    return cell;
  }

  private static HtmlContainer createCell(String tag, com.vaadin.flow.component.Component content, String className) {
    var cell = new HtmlContainer(tag, content);
    if (!className.isBlank()) {
      cell.addClassName(className);
    }
    return cell;
  }

  private static HorizontalLayout createCollapsedGroup(VaadinIcon icon, String label) {
    var row = new HorizontalLayout(new Icon(VaadinIcon.ANGLE_RIGHT), new Icon(icon), new Span(label));
    row.setPadding(false);
    row.setSpacing(false);
    row.setWidthFull();
    row.addClassName("c-runner-collapsed-group");
    return row;
  }
}
