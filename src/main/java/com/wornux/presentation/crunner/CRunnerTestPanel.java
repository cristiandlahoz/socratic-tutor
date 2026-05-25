package com.wornux.presentation.crunner;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HtmlContainer;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller.ScrollDirection;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.wornux.application.crunner.CDiagnosticSeverity;
import com.wornux.application.crunner.CDebugRequest;
import com.wornux.application.crunner.CDebugSessionResult;
import com.wornux.application.crunner.CDebugSnapshot;
import com.wornux.application.crunner.CDebugVariable;
import com.wornux.application.crunner.CProgramDebugService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@StyleSheet("styles/c-runner.css")
public final class CRunnerTestPanel extends Composite<Div> implements HasSize {

    private static final String DEFAULT_SOURCE = """
                                                 #include <stdio.h>

                                                 typedef struct {
                                                     int id;
                                                     char grade;
                                                     int scores[3];
                                                 } Student;

                                                 int sum_scores(Student student) {
                                                     int total = 0;
                                                     for (int i = 0; i < 3; i++) {
                                                         total += student.scores[i];
                                                     }
                                                     return total;
                                                 }

                                                 int main(void) {
                                                     Student student = {7, 'A', {8, 9, 10}};
                                                     int matrix[2][3] = {{1, 2, 3}, {4, 5, 6}};
                                                     int row_total = 0;
                                                     int bonus = 5;
                                                     int attempts = 2;
                                                     int limit = 100;
                                                     int passed = 0;
                                                     int min_score = student.scores[0];
                                                     int max_score = student.scores[0];
                                                     char section = 'B';
                                                     double average = 0.0;
                                                     int threshold = 25;
                                                     int normalized = 0;

                                                     for (int j = 0; j < 3; j++) {
                                                         row_total += matrix[1][j];
                                                         if (student.scores[j] < min_score) {
                                                             min_score = student.scores[j];
                                                         }
                                                         if (student.scores[j] > max_score) {
                                                             max_score = student.scores[j];
                                                         }
                                                     }

                                                     int *selected = &matrix[1][1];
                                                     int final_score = sum_scores(student) + row_total + *selected;
                                                     average = final_score / 3.0;
                                                     passed = final_score >= threshold;
                                                     normalized = final_score + bonus - attempts;

                                                     printf("student %d grade %c\\n", student.id, student.grade);
                                                     printf("section %c final score = %d / %d\\n", section, normalized, limit);
                                                     printf("average = %.2f passed = %d\\n", average, passed);
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
    private final TextArea stdinField = new TextArea("stdin");
    private final Pre stdoutText = new Pre("");
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
        stdinField.addClassName("c-runner-stdin");
        stdinField.setValueChangeMode(ValueChangeMode.EAGER);
        stdinField.setPlaceholder("stdin antes de ejecutar, ej: 42");
        stdoutText.addClassName("c-runner-stdout");

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
            renderStdout("");
            statusText.setText("");
        });

        var viewerShell = new Div(sourceViewer);
        viewerShell.addClassName("c-runner-viewer-shell");

        var codeFrame = new Div(viewerShell);
        codeFrame.addClassName("c-runner-code-frame");

        var root = getContent();
        root.setSizeFull();
        var content = new Div();
        content.addClassName("c-runner-panel");
        content.add(header, createStateCard(), controls, statusText, codeFrame, createTerminalCard());
        var scrollable = new Scroller(content, ScrollDirection.VERTICAL);
        scrollable.setSizeFull();
        scrollable.addClassName("c-runner-scroll-shell");
        root.add(scrollable);
        renderLocals(List.of());
        renderStdout("");
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
        stdinField.clear();
        renderStdout("");
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
            renderStdout("");
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
            var result = debugService.debug(new CDebugRequest(currentSource, "c17", "main.c", stdinField.getValue()));
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
            renderStdout("");
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
        renderStdout(snapshot.stdout());
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
            variable -> localsBody.add(createVariableRow(variable.name(), variable.value())));
    }

    private void renderStdout(String stdout) {
        var safeStdout = stdout == null || stdout.isBlank() ? "No hay salida" : stdout;
        stdoutText.setText(safeStdout);
        stdoutText.getElement().setAttribute("data-empty", Boolean.toString(stdout == null || stdout.isBlank()));
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
            createVariablesScroll());
        body.addClassName("c-runner-state-body");

        var card = new VerticalLayout(stateHeader, body);
        card.setPadding(false);
        card.setSpacing(false);
        card.setWidthFull();
        card.addClassName("c-runner-state-card");
        return card;
    }

    private Div createVariablesScroll() {
        var scroll = new Div(createVariablesTable());
        scroll.addClassName("c-runner-vars-scroll");
        return scroll;
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
                createCell("th", "Name", "c-runner-col-name"),
                createCell("th", "Value", "c-runner-col-value"));
    }

    private static HtmlContainer createVariableRow(String name, String value) {
        var nameText = new Span(name);
        nameText.addClassName("c-runner-var-name");

        var valueText = new Span(value);
        valueText.addClassName("c-runner-var-value");
        valueText.getElement().setAttribute("title", value == null ? "" : value);
        valueText.getElement().setAttribute("tabindex", "0");
        valueText.getElement().setAttribute("role", "button");
        valueText.getElement().setAttribute("aria-label", "Ver valor completo de " + name);
        attachValuePopover(name, value, valueText);

        return new HtmlContainer("tr",
                createCell("td", nameText, "c-runner-col-name"),
                createCell("td", valueText, "c-runner-col-value"));
    }

    private Div createTerminalCard() {
        var title = new Span("Terminal");
        title.addClassName("c-runner-terminal-title");

        var body = new Div(stdinField, createStdoutBlock());
        body.addClassName("c-runner-terminal-body");

        var card = new Div(title, body);
        card.addClassName("c-runner-terminal-card");
        return card;
    }

    private Div createStdoutBlock() {
        var label = new Span("stdout");
        label.addClassName("c-runner-terminal-label");

        var block = new Div(label, stdoutText);
        block.addClassName("c-runner-stdout-block");
        return block;
    }

    private static void attachValuePopover(String name, String value, Span target) {
        var title = new Span(name == null || name.isBlank() ? "value" : name);
        title.addClassName("c-runner-value-popover-title");

        var fullValue = new Pre(value == null || value.isBlank() ? "(empty)" : value);
        fullValue.addClassName("c-runner-value-popover-content");

        var popover = new Popover();
        popover.setTarget(target);
        popover.setModal(false);
        popover.addClassName("c-runner-value-popover");
        popover.add(new Div(title, fullValue));
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
