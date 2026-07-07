package com.wornux.ui.crunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.internal.JacksonUtils;
import com.vaadin.flow.shared.Registration;
import com.wornux.services.crunner.CDebugRequest;
import com.wornux.services.crunner.CDebugSessionResult;
import com.wornux.services.crunner.CDebugSnapshot;
import com.wornux.services.crunner.CDiagnosticSeverity;
import com.wornux.services.crunner.CExamplePreparationResult;
import com.wornux.services.crunner.CExamplePreparationService;
import com.wornux.services.crunner.CExamplePreparationStatus;
import com.wornux.services.crunner.CProgramDebugService;

@Tag("c-debugger-panel")
@JsModule("./crunner/c-debugger-panel.ts")
@StyleSheet("styles/c-runner.css")
public final class DebuggerPanel extends Component implements HasSize {

    private final CProgramDebugService debugService;
    private final CExamplePreparationService preparationService;
    private final Executor cRunnerExecutor;
    private List<com.wornux.services.crunner.CDiagnostic> currentDiagnostics = List.of();
    private List<CDebugSnapshot> snapshots = List.of();
    private String currentSource = "";
    private String currentStdin = "";
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
        setSizeFull();
        initializeClientState();
        addClosePanelRequestedListener(_ -> closeHandler.run());
        addSourceValueChangedListener(event -> handleSourceValueChanged(event.getValue()));
        addStdinValueChangedListener(event -> currentStdin = event.getValue());
        addValidateDebugRequestedListener(event -> {
            currentSource = event.getSourceValue();
            currentStdin = event.getStdin();
            debugCurrentSourceAsync();
        });
        addStepDebugRequestedListener(_ -> stepActiveLine());
        addResetDebugRequestedListener(_ -> resetActiveLine());
    }

    public void loadSource(String source) {
        setSource(source);
        setStatusText("Codigo cargado. Puedes editarlo antes de ejecutar.");
    }

    public void prepareAndDebugAssistantExample(String source, String lang) {
        var ui = UI.getCurrent();
        setSource("");
        var jobId = startAsyncJob("Preparando este ejemplo para que pueda ejecutarse...");
        if (ui == null) {
            renderPreparedDebug(jobId, prepareAndDebug(source, lang, currentStdin), null);
            return;
        }
        CompletableFuture.supplyAsync(() -> prepareAndDebug(source, lang, currentStdin), cRunnerExecutor)
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
        setStatusText("");
    }

    String statusTextForTesting() {
        return getElement().getProperty("statusText", "");
    }

    int activeLineForTesting() {
        return activeLine;
    }

    void stepActiveLine() {
        if (snapshots.isEmpty()) {
            setStatusText("Ejecuta primero");
            return;
        }
        snapshotIndex = Math.min(snapshotIndex + 1, snapshots.size() - 1);
        renderSnapshot(snapshots.get(snapshotIndex));
    }

    void resetActiveLine() {
        if (snapshots.isEmpty()) {
            setActiveLine(0);
            renderLocals(List.of());
            renderStdout("");
            setStatusText("");
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
            setStatusText("Pega codigo C antes de ejecutar.");
            return;
        }
        var ui = UI.getCurrent();
        var source = currentSource;
        var stdin = currentStdin;
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

    private void initializeClientState() {
        setSource("");
        setDiagnostics(List.of());
        setLocals(List.of());
        renderStdout("");
        setControlsEnabled(true);
        setEditable(true);
        getElement().setProperty("lang", "c");
        setStatusText("Pega codigo C o abre un ejemplo del asistente para visualizar la ejecucion.");
    }

    private void handleSourceValueChanged(String value) {
        debugJobSequence++;
        currentSource = value;
        currentDiagnostics = List.of();
        snapshots = List.of();
        snapshotIndex = 0;
        activeLine = 0;
        setDiagnostics(List.of());
        setActiveLine(0);
        renderLocals(List.of());
        renderStdout("");
        setControlsEnabled(true);
        setStatusText(
            currentSource.isBlank()
                    ? "Pega codigo C o abre un ejemplo del asistente para visualizar la ejecucion."
                    : "Codigo editado. Ejecuta para actualizar la visualizacion.");
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
        setStatusText(status);
        return jobId;
    }

    private void renderPreparedDebug(long jobId, PreparedDebugResult preparedResult, Throwable ex) {
        if (jobId != debugJobSequence) {
            return;
        }
        setControlsEnabled(true);
        if (ex != null) {
            setStatusText("No se pudo preparar el ejemplo. Intenta pegar el codigo manualmente.");
            return;
        }
        var preparation = preparedResult.preparation();
        if (preparation.status() != CExamplePreparationStatus.READY) {
            currentSource = preparedResult.originalSource() == null ? "" : preparedResult.originalSource();
            setSource(currentSource);
            setStatusText(preparation.educationalNote());
            return;
        }
        currentSource = preparation.source();
        setSource(currentSource);
        if (preparedResult.debugResult() == null) {
            setStatusText(preparation.educationalNote());
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
            setStatusText("No se pudo ejecutar el debugger.");
            return;
        }
        currentSource = source;
        renderDebugResult(result);
    }

    private void setSource(String source) {
        debugJobSequence++;
        currentSource = source == null ? "" : source;
        getElement().setProperty("source", currentSource);
        setDiagnostics(List.of());
        currentDiagnostics = List.of();
        snapshots = List.of();
        snapshotIndex = 0;
        currentDebugger = "";
        currentDebugElapsedMs = 0;
        activeLine = 0;
        renderLocals(List.of());
        setActiveLine(0);
        currentStdin = "";
        getElement().setProperty("stdin", "");
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
        setDiagnostics(List.of());
        setActiveLine(0);
        renderLocals(List.of());
        renderStdout("");
    }

    private void setControlsEnabled(boolean enabled) {
        getElement().setProperty("controlsEnabled", enabled);
    }

    private void setEditable(boolean editable) {
        getElement().setProperty("editable", editable);
    }

    private void setStatusText(String statusText) {
        getElement().setProperty("statusText", statusText == null ? "" : statusText);
    }

    private void setDiagnostics(List<com.wornux.services.crunner.CDiagnostic> diagnostics) {
        var safeDiagnostics = diagnostics == null ? List.<com.wornux.services.crunner.CDiagnostic>of() : diagnostics;
        getElement().setPropertyJson("diagnostics", JacksonUtils.listToJson(safeDiagnostics));
    }

    private void setActiveLine(int activeLine) {
        this.activeLine = activeLine;
        getElement().setProperty("activeLine", activeLine);
    }

    private void renderDebugResult(CDebugSessionResult result) {
        getElement().setProperty("source", currentSource);
        setDiagnostics(result.diagnostics());
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
            setActiveLine(0);
            setStatusText("Debugger | %d error(es) | %s | %d ms".formatted(errors, result.compiler(), result.elapsedMs()));
            return;
        }

        renderSnapshot(snapshots.getFirst());
    }

    private void renderSnapshot(CDebugSnapshot snapshot) {
        setActiveLine(snapshot.line() == null ? 0 : snapshot.line());
        renderLocals(snapshot.locals());
        renderStdout(snapshot.stdout());
        if (!snapshots.isEmpty()) {
            setStatusText(
                "Snapshot %d/%d | %s | %d ms"
                        .formatted(snapshotIndex + 1, snapshots.size(), currentDebugger, currentDebugElapsedMs));
        }
    }

    private void renderLocals(List<com.wornux.services.crunner.CDebugVariable> locals) {
        var safeLocals = locals == null ? List.<com.wornux.services.crunner.CDebugVariable>of() : locals;
        setLocals(safeLocals);
    }

    private void setLocals(List<com.wornux.services.crunner.CDebugVariable> locals) {
        getElement().setPropertyJson("locals", JacksonUtils.listToJson(locals));
    }

    private void renderStdout(String stdout) {
        getElement().setProperty("stdout", stdout == null ? "" : stdout);
    }

    @SuppressWarnings("unused")
    private void showDiagnosticsSummary() {
        if (currentDiagnostics.isEmpty()) {
            setStatusText("Sin diagnosticos");
            return;
        }
        var first = currentDiagnostics.getFirst();
        setStatusText("%s linea %d: %s".formatted(first.severity(), first.line(), first.message()));
    }

    private Registration addClosePanelRequestedListener(ComponentEventListener<ClosePanelRequestedEvent> listener) {
        return addListener(ClosePanelRequestedEvent.class, listener);
    }

    private Registration addSourceValueChangedListener(ComponentEventListener<SourceValueChangedEvent> listener) {
        return addListener(SourceValueChangedEvent.class, listener);
    }

    private Registration addStdinValueChangedListener(ComponentEventListener<StdinValueChangedEvent> listener) {
        return addListener(StdinValueChangedEvent.class, listener);
    }

    private Registration addValidateDebugRequestedListener(ComponentEventListener<ValidateDebugRequestedEvent> listener) {
        return addListener(ValidateDebugRequestedEvent.class, listener);
    }

    private Registration addStepDebugRequestedListener(ComponentEventListener<StepDebugRequestedEvent> listener) {
        return addListener(StepDebugRequestedEvent.class, listener);
    }

    private Registration addResetDebugRequestedListener(ComponentEventListener<ResetDebugRequestedEvent> listener) {
        return addListener(ResetDebugRequestedEvent.class, listener);
    }

    @DomEvent("close-panel-requested")
    public static final class ClosePanelRequestedEvent extends ComponentEvent<DebuggerPanel> {

        public ClosePanelRequestedEvent(DebuggerPanel source, boolean fromClient) {
            super(source, fromClient);
        }
    }

    @DomEvent("source-value-changed")
    public static final class SourceValueChangedEvent extends ComponentEvent<DebuggerPanel> {

        private final String value;

        public SourceValueChangedEvent(
                DebuggerPanel source,
                boolean fromClient,
                @EventData("event.detail.value") String value) {
            super(source, fromClient);
            this.value = value == null ? "" : value;
        }

        public String getValue() {
            return value;
        }
    }

    @DomEvent("stdin-value-changed")
    public static final class StdinValueChangedEvent extends ComponentEvent<DebuggerPanel> {

        private final String value;

        public StdinValueChangedEvent(
                DebuggerPanel source,
                boolean fromClient,
                @EventData("event.detail.value") String value) {
            super(source, fromClient);
            this.value = value == null ? "" : value;
        }

        public String getValue() {
            return value;
        }
    }

    @DomEvent("validate-debug-requested")
    public static final class ValidateDebugRequestedEvent extends ComponentEvent<DebuggerPanel> {

        private final String sourceValue;
        private final String stdin;

        public ValidateDebugRequestedEvent(
                DebuggerPanel source,
                boolean fromClient,
                @EventData("event.detail.source") String sourceValue,
                @EventData("event.detail.stdin") String stdin) {
            super(source, fromClient);
            this.sourceValue = sourceValue == null ? "" : sourceValue;
            this.stdin = stdin == null ? "" : stdin;
        }

        public String getSourceValue() {
            return sourceValue;
        }

        public String getStdin() {
            return stdin;
        }
    }

    @DomEvent("step-debug-requested")
    public static final class StepDebugRequestedEvent extends ComponentEvent<DebuggerPanel> {

        public StepDebugRequestedEvent(DebuggerPanel source, boolean fromClient) {
            super(source, fromClient);
        }
    }

    @DomEvent("reset-debug-requested")
    public static final class ResetDebugRequestedEvent extends ComponentEvent<DebuggerPanel> {

        public ResetDebugRequestedEvent(DebuggerPanel source, boolean fromClient) {
            super(source, fromClient);
        }
    }

    private record PreparedDebugResult(String originalSource, CExamplePreparationResult preparation,
            CDebugSessionResult debugResult) {}
}
