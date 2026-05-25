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
import com.wornux.application.crunner.CDebugSessionResult;
import com.wornux.application.crunner.CDebugSnapshot;
import com.wornux.application.crunner.CDebugVariable;
import com.wornux.application.crunner.CProgramDebugService;
import com.wornux.application.crunner.CSourceRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@StyleSheet("styles/c-runner.css")
public final class CRunnerTestPanel extends Composite<Div> implements HasSize {

    private static final String DEFAULT_SOURCE = """
                                                 #include <stdio.h>

                                                 int main(void) {
                                                     int variable = 41;
                                                     char c = 'a';
                                                     printf("variable = %d\\n", variable);
                                                     printf("c = %c\\n", c);
                                                     return 0;
                                                 }
                                                 """;

    private final CProgramDebugService debugService;
    private final Button validateButton = createIconButton(VaadinIcon.PLAY, "Ejecutar depuracion");
    private final Button stepButton = createIconButton(VaadinIcon.ARROW_RIGHT, "Paso siguiente");
    private final Button resetButton = createIconButton(VaadinIcon.ROTATE_LEFT, "Reiniciar");
    private final Button menuButton = createIconButton(VaadinIcon.ELLIPSIS_V, "Diagnosticos");
    private final Span statusText = new Span("");
    private final CDebugSourceViewer sourceViewer = new CDebugSourceViewer();
    private final Span localsLabel = new Span("Variables");
    private final Span variableCount = new Span();
    private final HtmlContainer localsBody = new HtmlContainer("tbody");
    private List<com.wornux.application.crunner.CDiagnostic> currentDiagnostics = List.of();
    private List<CDebugSnapshot> snapshots = List.of();
    private String currentSource = DEFAULT_SOURCE;
    private String currentDebugger = "";
    private long currentDebugElapsedMs = 0;
    private int activeLine = 0;
    private int snapshotIndex = 0;

    public CRunnerTestPanel(CProgramDebugService debugService) {
        this.debugService = Objects.requireNonNull(debugService, "debugService must not be null");

        var title = new H2("Code Visualizer");
        title.addClassName("c-runner-title");

        var header = new HorizontalLayout(title);
        header.setPadding(false);
        header.setSpacing(false);
        header.setWidthFull();
        header.addClassName("c-runner-header");

        statusText.addClassName("c-runner-status-text");
        localsLabel.addClassName("c-runner-group-label");
        variableCount.addClassName("c-runner-state-pill");

        validateButton.addClassName("c-runner-validate-button");
        validateButton.addClickListener(_ -> debugCurrentSource());
        stepButton.addClickListener(_ -> stepActiveLine());
        resetButton.addClickListener(_ -> resetActiveLine());
        menuButton.addClickListener(_ -> showDiagnosticsSummary());

        var controls = new HorizontalLayout(validateButton, stepButton, resetButton, menuButton);
        controls.setPadding(false);
        controls.setSpacing(false);
        controls.addClassName("c-runner-controls");

        sourceViewer.setValue(DEFAULT_SOURCE);
        sourceViewer.setEditable(true);
        sourceViewer.setDiagnostics(List.of());
        sourceViewer.setActiveLine(activeLine);
        sourceViewer.setSizeFull();
        sourceViewer.addClassName("c-runner-source-viewer");
        sourceViewer.addValueChangeListener(event -> {
            currentSource = event.getValue();
            currentDiagnostics = List.of();
            snapshots = List.of();
            snapshotIndex = 0;
            activeLine = 0;
            sourceViewer.setDiagnostics(List.of());
            sourceViewer.setActiveLine(0);
            renderLocals(List.of());
            statusText.setText("");
        });

        var viewerShell = new Div(sourceViewer);
        viewerShell.addClassName("c-runner-viewer-shell");

        var codeFrame = new Div(viewerShell);
        codeFrame.addClassName("c-runner-code-frame");

        var root = getContent();
        root.setSizeFull();
        root.addClassName("c-runner-panel");
        root.add(header, createStateCard(), controls, statusText, codeFrame);
        renderLocals(List.of());
    }

    void setSourceForTesting(String source) {
        currentSource = source == null ? "" : source;
        sourceViewer.setValue(currentSource);
        sourceViewer.setDiagnostics(List.of());
        currentDiagnostics = List.of();
        snapshots = List.of();
        snapshotIndex = 0;
        currentDebugger = "";
        currentDebugElapsedMs = 0;
        activeLine = 0;
        renderLocals(List.of());
        sourceViewer.setActiveLine(0);
        statusText.setText("");
    }

    String statusTextForTesting() {
        return statusText.getText();
    }

    int activeLineForTesting() {
        return activeLine;
    }

    void stepActiveLine() {
        if (snapshots.isEmpty()) {
            statusText.setText("Ejecuta primero");
            return;
        }
        snapshotIndex = Math.min(snapshotIndex + 1, snapshots.size() - 1);
        renderSnapshot(snapshots.get(snapshotIndex));
    }

    void resetActiveLine() {
        if (snapshots.isEmpty()) {
            sourceViewer.setActiveLine(0);
            renderLocals(List.of());
            statusText.setText("");
            return;
        }
        snapshotIndex = 0;
        renderSnapshot(snapshots.getFirst());
    }

    void debugCurrentSource() {
        validateButton.setEnabled(false);
        statusText.setText("Depurando...");
        try {
            var result = debugService.debug(new CSourceRequest(currentSource, "c17", "main.c"));
            renderDebugResult(result);
        }
        finally {
            validateButton.setEnabled(true);
        }
    }

    private void renderDebugResult(CDebugSessionResult result) {
        sourceViewer.setValue(currentSource);
        sourceViewer.setDiagnostics(result.diagnostics());
        currentDiagnostics = new ArrayList<>(result.diagnostics());
        snapshots = result.snapshots();
        snapshotIndex = 0;
        currentDebugger = result.compiler();
        currentDebugElapsedMs = result.elapsedMs();

        if (!result.valid() || snapshots.isEmpty()) {
            var errors = result.diagnostics()
                    .stream()
                    .filter(diagnostic -> diagnostic.severity() == CDiagnosticSeverity.ERROR)
                    .count();
            renderLocals(List.of());
            sourceViewer.setActiveLine(0);
            statusText.setText(
                "Debugger | %d error(es) | %s | %d ms".formatted(errors, result.compiler(), result.elapsedMs()));
            return;
        }

        renderSnapshot(snapshots.getFirst());
    }

    private void renderSnapshot(CDebugSnapshot snapshot) {
        activeLine = snapshot.line() == null ? 0 : snapshot.line();
        sourceViewer.setActiveLine(activeLine);
        renderLocals(snapshot.locals());
        if (!snapshots.isEmpty()) {
            statusText.setText(
                "Snapshot %d/%d | %s | %d ms"
                        .formatted(snapshotIndex + 1, snapshots.size(), currentDebugger, currentDebugElapsedMs));
        }
    }

    private void renderLocals(List<CDebugVariable> locals) {
        localsBody.removeAll();
        var safeLocals = locals == null ? List.<CDebugVariable>of() : locals;
        variableCount.setText("%d vars".formatted(safeLocals.size()));
        safeLocals.forEach(
            variable -> localsBody.add(createVariableRow(variable.type(), variable.name(), variable.value())));
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

    private VerticalLayout createStateCard() {
        var stateTitle = new Span("State");
        stateTitle.addClassName("c-runner-state-title");

        var stateHeader = new HorizontalLayout(stateTitle, variableCount);
        stateHeader.setPadding(false);
        stateHeader.setSpacing(false);
        stateHeader.setWidthFull();
        stateHeader.expand(stateTitle);
        stateHeader.addClassName("c-runner-state-header");

        var localsGroup = new HorizontalLayout(new Icon(VaadinIcon.EYE), localsLabel);
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
            createVariablesTable());
        body.addClassName("c-runner-state-body");

        var card = new VerticalLayout(stateHeader, body);
        card.setPadding(false);
        card.setSpacing(false);
        card.setWidthFull();
        card.addClassName("c-runner-state-card");
        return card;
    }

    private HtmlContainer createVariablesTable() {
        var table = new HtmlContainer("table");
        table.addClassName("c-runner-vars-table");

        var thead = new HtmlContainer("thead", createHeaderRow());
        table.add(thead, localsBody);
        return table;
    }

    private static HtmlContainer createHeaderRow() {
        return new HtmlContainer("tr",
                createCell("th", "Type", "c-runner-col-type"),
                createCell("th", "Name", ""),
                createCell("th", "Value", ""));
    }

    private static HtmlContainer createVariableRow(String type, String name, String value) {
        var typeChip = new Span(type);
        typeChip.addClassName("c-runner-type-chip");

        var nameText = new Span(name);
        nameText.addClassName("c-runner-var-name");

        var valueText = new Span(value);
        valueText.addClassName("c-runner-var-value");

        return new HtmlContainer("tr",
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

}
