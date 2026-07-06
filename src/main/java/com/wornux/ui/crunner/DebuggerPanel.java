package com.wornux.ui.crunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HtmlContainer;
import com.vaadin.flow.component.UI;
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
import com.vaadin.flow.component.orderedlayout.Scroller.ScrollDirection;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.wornux.services.crunner.CDebugRequest;
import com.wornux.services.crunner.CDebugSessionResult;
import com.wornux.services.crunner.CDebugSnapshot;
import com.wornux.services.crunner.CDebugVariable;
import com.wornux.services.crunner.CDiagnosticSeverity;
import com.wornux.services.crunner.CExamplePreparationResult;
import com.wornux.services.crunner.CExamplePreparationService;
import com.wornux.services.crunner.CExamplePreparationStatus;
import com.wornux.services.crunner.CProgramDebugService;
import com.wornux.ui.components.ToggleIcon;
import com.wornux.ui.css.CssClass;
import com.wornux.ui.css.UiCss;

@StyleSheet("styles/c-runner.css")
public final class DebuggerPanel extends Composite<Div> implements HasSize {

    private final CProgramDebugService debugService;
    private final CExamplePreparationService preparationService;
    private final Executor cRunnerExecutor;
    private final Button validateButton = createIconButton(VaadinIcon.PLAY, "Ejecutar depuracion");
    private final Button stepButton = createIconButton(VaadinIcon.ARROW_RIGHT, "Paso siguiente");
    private final Button resetButton = createIconButton(VaadinIcon.ROTATE_LEFT, "Reiniciar");
    private final ToggleIcon toggle = createPanelToggleButton();
    private final Span statusText = new Span("");
    private final DebugSourceViewer sourceViewer = new DebugSourceViewer();
    private final TextArea stdinField = new TextArea("stdin");
    private final Pre stdoutText = new Pre("");
    private final Span localsLabel = new Span("Variables");
    private final Span variableCount = new Span();
    private final HtmlContainer localsBody = new HtmlContainer("tbody");
    private List<com.wornux.services.crunner.CDiagnostic> currentDiagnostics = List.of();
    private List<CDebugSnapshot> snapshots = List.of();
    private String currentSource = "";
    private String currentDebugger = "";
    private long currentDebugElapsedMs = 0;
    private int activeLine = 0;
    private int snapshotIndex = 0;
    private long debugJobSequence = 0;
    private Runnable closeHandler = () -> {};

    public DebuggerPanel(
            CProgramDebugService debugService,
            CExamplePreparationService preparationService,
            Executor cRunnerExecutor) {
        this.debugService = Objects.requireNonNull(debugService, "debugService must not be null");
        this.preparationService = Objects.requireNonNull(preparationService, "preparationService must not be null");
        this.cRunnerExecutor = Objects.requireNonNull(cRunnerExecutor, "cRunnerExecutor must not be null");

        var title = new H2("Depurador Visual");
        UiCss.C_RUNNER_TITLE.addTo(title);

        toggle.addClickListener(_ -> closeHandler.run());

        var header = new HorizontalLayout(toggle, title);
        header.setPadding(false);
        header.setSpacing(false);
        header.setWidthFull();
        UiCss.C_RUNNER_HEADER.addTo(header);

        UiCss.C_RUNNER_STATUS_TEXT.addTo(statusText);
        statusText.setText("Pega codigo C o abre un ejemplo del asistente para visualizar la ejecucion.");
        UiCss.C_RUNNER_GROUP_LABEL.addTo(localsLabel);
        UiCss.C_RUNNER_STATE_PILL.addTo(variableCount);

        UiCss.C_RUNNER_VALIDATE_BUTTON.addTo(validateButton);
        validateButton.addClickListener(_ -> debugCurrentSourceAsync());
        stepButton.addClickListener(_ -> stepActiveLine());
        resetButton.addClickListener(_ -> resetActiveLine());
        UiCss.C_RUNNER_STDIN.addTo(stdinField);
        stdinField.setValueChangeMode(ValueChangeMode.EAGER);
        stdinField.setPlaceholder("stdin antes de ejecutar, ej: 42");
        UiCss.C_RUNNER_STDOUT.addTo(stdoutText);

        var controls = new HorizontalLayout(validateButton, stepButton, resetButton);
        controls.setPadding(false);
        controls.setSpacing(false);
        UiCss.C_RUNNER_CONTROLS.addTo(controls);

        sourceViewer.setValue("");
        sourceViewer.setEditable(true);
        sourceViewer.setDiagnostics(List.of());
        sourceViewer.setActiveLine(activeLine);
        sourceViewer.setSizeFull();
        UiCss.C_RUNNER_SOURCE_VIEWER.addTo(sourceViewer);
        sourceViewer.addValueChangeListener(event -> {
            debugJobSequence++;
            currentSource = event.getValue();
            currentDiagnostics = List.of();
            snapshots = List.of();
            snapshotIndex = 0;
            activeLine = 0;
            sourceViewer.setDiagnostics(List.of());
            sourceViewer.setActiveLine(0);
            renderLocals(List.of());
            renderStdout("");
            setControlsEnabled(true);
            statusText.setText(
                currentSource.isBlank()
                        ? "Pega codigo C o abre un ejemplo del asistente para visualizar la ejecucion."
                        : "Codigo editado. Ejecuta para actualizar la visualizacion.");
        });

        var viewerShell = new Div(sourceViewer);
        UiCss.C_RUNNER_VIEWER_SHELL.addTo(viewerShell);

        var codeFrame = new Div(viewerShell);
        UiCss.C_RUNNER_CODE_FRAME.addTo(codeFrame);

        var root = getContent();
        root.setSizeFull();
        var content = new Div();
        UiCss.C_RUNNER_PANEL.addTo(content);
        content.add(header, createStateCard(), controls, statusText, codeFrame, createTerminalCard());
        var scrollable = new Scroller(content, ScrollDirection.VERTICAL);
        scrollable.setSizeFull();
        UiCss.C_RUNNER_SCROLL_SHELL.addTo(scrollable);
        root.add(scrollable);
        renderLocals(List.of());
        renderStdout("");
    }

    public void loadSource(String source) {
        setSource(source);
        statusText.setText("Codigo cargado. Puedes editarlo antes de ejecutar.");
    }

    public void prepareAndDebugAssistantExample(String source, String lang) {
        var ui = UI.getCurrent();
        var stdin = stdinField.getValue();
        setSource("");
        var jobId = startAsyncJob("Preparando este ejemplo para que pueda ejecutarse...");
        if (ui == null) {
            renderPreparedDebug(jobId, prepareAndDebug(source, lang, stdin), null);
            return;
        }
        CompletableFuture.supplyAsync(() -> prepareAndDebug(source, lang, stdin), cRunnerExecutor)
                .whenComplete((preparedResult, ex) -> ui.access(() -> renderPreparedDebug(jobId, preparedResult, ex)));
    }

    public boolean hasUserContent() {
        return !currentSource.isBlank();
    }

    public void setCloseHandler(Runnable closeHandler) {
        this.closeHandler = closeHandler == null ? () -> {} : closeHandler;
    }

    void setSourceForTesting(String source) {
        setSource(source);
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
        debugCurrentSourceAsync();
    }

    void debugCurrentSourceAsync() {
        if (currentSource.isBlank()) {
            statusText.setText("Pega codigo C antes de ejecutar.");
            return;
        }
        var ui = UI.getCurrent();
        var source = currentSource;
        var stdin = stdinField.getValue();
        var jobId = startAsyncJob("Depurando...");
        if (ui == null) {
            renderDebugJob(jobId, source, debugService.debug(new CDebugRequest(source, "c17", "main.c", stdin)), null);
            return;
        }
        CompletableFuture
                .supplyAsync(
                    () -> debugService.debug(new CDebugRequest(source, "c17", "main.c", stdin)),
                    cRunnerExecutor)
                .whenComplete((result, ex) -> ui.access(() -> renderDebugJob(jobId, source, result, ex)));
    }

    private PreparedDebugResult prepareAndDebug(String source, String lang, String stdin) {
        var preparation = preparationService.prepare(source, lang);
        if (!preparation.ready()) {
            return new PreparedDebugResult(source, preparation, null);
        }
        var result = debugService.debug(new CDebugRequest(preparation.source(), "c17", "main.c", stdin));
        return new PreparedDebugResult(source, preparation, result);
    }

    private long startAsyncJob(String status) {
        var jobId = ++debugJobSequence;
        setControlsEnabled(false);
        clearDebugState();
        statusText.setText(status);
        return jobId;
    }

    private void renderPreparedDebug(long jobId, PreparedDebugResult preparedResult, Throwable ex) {
        if (jobId != debugJobSequence) {
            return;
        }
        setControlsEnabled(true);
        if (ex != null) {
            statusText.setText("No se pudo preparar el ejemplo. Intenta pegar el codigo manualmente.");
            return;
        }
        var preparation = preparedResult.preparation();
        if (preparation.status() != CExamplePreparationStatus.READY) {
            currentSource = preparedResult.originalSource() == null ? "" : preparedResult.originalSource();
            sourceViewer.setValue(currentSource);
            statusText.setText(preparation.educationalNote());
            return;
        }
        currentSource = preparation.source();
        sourceViewer.setValue(currentSource);
        if (preparedResult.debugResult() == null) {
            statusText.setText(preparation.educationalNote());
            return;
        }
        renderDebugResult(preparedResult.debugResult());
    }

    private void renderDebugJob(long jobId, String source, CDebugSessionResult result, Throwable ex) {
        if (jobId != debugJobSequence) {
            return;
        }
        setControlsEnabled(true);
        if (ex != null) {
            statusText.setText("No se pudo ejecutar el debugger.");
            return;
        }
        currentSource = source;
        renderDebugResult(result);
    }

    private void setSource(String source) {
        debugJobSequence++;
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
        setControlsEnabled(true);
    }

    private void clearDebugState() {
        currentDiagnostics = List.of();
        snapshots = List.of();
        snapshotIndex = 0;
        activeLine = 0;
        currentDebugger = "";
        currentDebugElapsedMs = 0;
        sourceViewer.setDiagnostics(List.of());
        sourceViewer.setActiveLine(0);
        renderLocals(List.of());
        renderStdout("");
    }

    private void setControlsEnabled(boolean enabled) {
        validateButton.setEnabled(enabled);
        stepButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        stdinField.setEnabled(enabled);
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
        safeLocals.forEach(variable -> localsBody.add(createVariableRow(variable.name(), variable.value())));
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
        UiCss.C_RUNNER_CONTROL_BUTTON.addTo(button);
        button.getElement().setAttribute("aria-label", label);
        button.getElement().setAttribute("title", label);
        return button;
    }

    private static ToggleIcon createPanelToggleButton() {
        var toggle = new ToggleIcon();
        UiCss.C_RUNNER_PANEL_TOGGLE.addTo(toggle);
        toggle.getElement().setAttribute("title", "Ocultar depurador");
        return toggle;
    }

    private VerticalLayout createStateCard() {
        var stateTitle = new Span("Estado");
        UiCss.C_RUNNER_STATE_TITLE.addTo(stateTitle);

        var stateHeader = new HorizontalLayout(stateTitle, variableCount);
        stateHeader.setPadding(false);
        stateHeader.setSpacing(false);
        stateHeader.setWidthFull();
        stateHeader.expand(stateTitle);
        UiCss.C_RUNNER_STATE_HEADER.addTo(stateHeader);

        var localsGroup = new HorizontalLayout(new Icon(VaadinIcon.EYE), localsLabel);
        localsGroup.setPadding(false);
        localsGroup.setSpacing(false);
        localsGroup.setWidthFull();
        UiCss.C_RUNNER_STATE_GROUP.addTo(localsGroup);

        var body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(false);
        body.setWidthFull();
        body.add(localsGroup, createVariablesScroll());
        UiCss.C_RUNNER_STATE_BODY.addTo(body);

        var card = new VerticalLayout(stateHeader, body);
        card.setPadding(false);
        card.setSpacing(false);
        card.setWidthFull();
        UiCss.C_RUNNER_STATE_CARD.addTo(card);
        return card;
    }

    private Div createVariablesScroll() {
        var scroll = new Div(createVariablesTable());
        UiCss.C_RUNNER_VARS_SCROLL.addTo(scroll);
        return scroll;
    }

    private HtmlContainer createVariablesTable() {
        var table = new HtmlContainer("table");
        UiCss.C_RUNNER_VARS_TABLE.addTo(table);

        var thead = new HtmlContainer("thead", createHeaderRow());
        table.add(thead, localsBody);
        return table;
    }

    private static HtmlContainer createHeaderRow() {
        return new HtmlContainer("tr",
                createCell("th", "Name", UiCss.C_RUNNER_COL_NAME),
                createCell("th", "Value", UiCss.C_RUNNER_COL_VALUE));
    }

    private static HtmlContainer createVariableRow(String name, String value) {
        var nameText = new Span(name);
        UiCss.C_RUNNER_VAR_NAME.addTo(nameText);

        var valueText = new Span(value);
        UiCss.C_RUNNER_VAR_VALUE.addTo(valueText);
        valueText.getElement().setAttribute("title", value == null ? "" : value);
        valueText.getElement().setAttribute("tabindex", "0");
        valueText.getElement().setAttribute("role", "button");
        valueText.getElement().setAttribute("aria-label", "Ver valor completo de %s".formatted(name));
        attachValuePopover(name, value, valueText);

        return new HtmlContainer("tr",
                createCell("td", nameText, UiCss.C_RUNNER_COL_NAME),
                createCell("td", valueText, UiCss.C_RUNNER_COL_VALUE));
    }

    private Div createTerminalCard() {
        var title = new Span("Terminal");
        UiCss.C_RUNNER_TERMINAL_TITLE.addTo(title);

        var body = new Div(stdinField, createStdoutBlock());
        UiCss.C_RUNNER_TERMINAL_BODY.addTo(body);

        var card = new Div(title, body);
        UiCss.C_RUNNER_TERMINAL_CARD.addTo(card);
        return card;
    }

    private Div createStdoutBlock() {
        var label = new Span("stdout");
        UiCss.C_RUNNER_TERMINAL_LABEL.addTo(label);

        var block = new Div(label, stdoutText);
        UiCss.C_RUNNER_STDOUT_BLOCK.addTo(block);
        return block;
    }

    private static void attachValuePopover(String name, String value, Span target) {
        var title = new Span(name == null || name.isBlank() ? "value" : name);
        UiCss.C_RUNNER_VALUE_POPOVER_TITLE.addTo(title);

        var fullValue = new Pre(value == null || value.isBlank() ? "(empty)" : value);
        UiCss.C_RUNNER_VALUE_POPOVER_CONTENT.addTo(fullValue);

        var popover = new Popover();
        popover.setTarget(target);
        popover.setModal(false);
        UiCss.C_RUNNER_VALUE_POPOVER.addTo(popover);
        popover.add(new Div(title, fullValue));
    }

    private static HtmlContainer createCell(String tag, String text, CssClass className) {
        var cell = new HtmlContainer(tag);
        cell.setText(text);
        className.addTo(cell);
        return cell;
    }

    private static HtmlContainer createCell(
            String tag,
            com.vaadin.flow.component.Component content,
            CssClass className) {
        var cell = new HtmlContainer(tag, content);
        className.addTo(cell);
        return cell;
    }

    private record PreparedDebugResult(String originalSource, CExamplePreparationResult preparation,
            CDebugSessionResult debugResult) {}

}
