package com.wornux.ui.ingestion;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.services.document.CourseMaterialCatalog;
import com.wornux.ui.MainLayout;

@SpringComponent
@RouteScope
@RouteScopeOwner(MainLayout.class)
public class DocumentIngestionState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ValueSignal<String> activeIngestionId = new ValueSignal<>(null);
    private final ValueSignal<List<String>> indexedVectorIds = new ValueSignal<>(List.of());
    private final ValueSignal<String> fileName = new ValueSignal<>("");
    private final ValueSignal<String> stageLabel = new ValueSignal<>("Sube un PDF para comenzar.");
    private final ValueSignal<String> failureMessage = new ValueSignal<>("");
    private final ValueSignal<String> reviewedMarkdown = new ValueSignal<>("");
    private final ValueSignal<String> catalogUseWhen = new ValueSignal<>("");
    private final ValueSignal<Optional<CourseMaterialCatalog>> generatedCatalog = new ValueSignal<>(Optional.empty());
    private final ValueSignal<List<EditableSegmentViewModel>> segments = new ValueSignal<>(List.of());
    private final ValueSignal<Boolean> busy = new ValueSignal<>(false);
    private final ValueSignal<Boolean> indexed = new ValueSignal<>(false);
    private final ValueSignal<Boolean> dirty = new ValueSignal<>(false);
    private final ValueSignal<Boolean> retryAvailable = new ValueSignal<>(false);

    private final Signal<Boolean> reviewVisible = Signal.computed(() -> activeIngestionId.get() != null);
    private final Signal<Boolean> canGenerateCatalog = Signal.computed(
        () -> !busy.get()
                && activeIngestionId.get() != null
                && !indexed.get()
                && !catalogUseWhen.get().isBlank()
                && catalogUseWhen.get().length() <= 200
                && !segments.get().isEmpty());
    private final Signal<Boolean> canApprove = Signal.computed(
        () -> !busy.get()
                && activeIngestionId.get() != null
                && !indexed.get()
                && !reviewedMarkdown.get().isBlank()
                && generatedCatalog.get().isPresent()
                && !segments.get().isEmpty()
                && segments.get()
                        .stream()
                        .allMatch(segment -> segment.content() != null && !segment.content().isBlank()));

    public ValueSignal<String> activeIngestionId() {
        return activeIngestionId;
    }

    public ValueSignal<List<String>> indexedVectorIds() {
        return indexedVectorIds;
    }

    public ValueSignal<String> fileName() {
        return fileName;
    }

    public ValueSignal<String> stageLabel() {
        return stageLabel;
    }

    public ValueSignal<String> failureMessage() {
        return failureMessage;
    }

    public ValueSignal<String> reviewedMarkdown() {
        return reviewedMarkdown;
    }

    public ValueSignal<String> catalogUseWhen() {
        return catalogUseWhen;
    }

    public ValueSignal<Optional<CourseMaterialCatalog>> generatedCatalog() {
        return generatedCatalog;
    }

    public ValueSignal<List<EditableSegmentViewModel>> segments() {
        return segments;
    }

    public ValueSignal<Boolean> busy() {
        return busy;
    }

    public ValueSignal<Boolean> indexed() {
        return indexed;
    }

    public ValueSignal<Boolean> dirty() {
        return dirty;
    }

    public ValueSignal<Boolean> retryAvailable() {
        return retryAvailable;
    }

    public Signal<Boolean> reviewVisible() {
        return reviewVisible;
    }

    public Signal<Boolean> canGenerateCatalog() {
        return canGenerateCatalog;
    }

    public Signal<Boolean> canApprove() {
        return canApprove;
    }

    public void startProcessing(String fileName, String stageLabel) {
        this.fileName.set(fileName == null ? "" : fileName);
        this.stageLabel.set(stageLabel);
        this.failureMessage.set("");
        this.busy.set(true);
        this.retryAvailable.set(false);
    }

    public void startUploadProcessing(String fileName, String stageLabel) {
        activeIngestionId.set(null);
        indexedVectorIds.set(List.of());
        reviewedMarkdown.set("");
        catalogUseWhen.set("");
        generatedCatalog.set(Optional.empty());
        segments.set(List.of());
        indexed.set(false);
        dirty.set(false);
        startProcessing(fileName, stageLabel);
    }

    public void apply(DocumentReviewViewModel reviewVm) {
        activeIngestionId.set(reviewVm.ingestionId());
        indexedVectorIds.set(List.copyOf(reviewVm.vectorIds()));
        fileName.set(reviewVm.filename());
        stageLabel.set(reviewVm.stageLabel());
        failureMessage.set("");
        reviewedMarkdown.set(reviewVm.markdown());
        catalogUseWhen.set("");
        generatedCatalog.set(Optional.empty());
        segments.set(List.copyOf(reviewVm.segments()));
        busy.set(false);
        indexed.set(reviewVm.indexed());
        dirty.set(false);
        retryAvailable.set(false);
    }

    public void markFailure(String message, boolean retryAvailable) {
        this.failureMessage.set(message == null ? "Ocurrió un error inesperado." : message);
        this.stageLabel.set("La ingestion fallo.");
        this.busy.set(false);
        this.retryAvailable.set(retryAvailable);
    }

    public void reset() {
        activeIngestionId.set(null);
        indexedVectorIds.set(List.of());
        fileName.set("");
        stageLabel.set("Sube un PDF para comenzar.");
        failureMessage.set("");
        reviewedMarkdown.set("");
        catalogUseWhen.set("");
        generatedCatalog.set(Optional.empty());
        segments.set(List.of());
        busy.set(false);
        indexed.set(false);
        dirty.set(false);
        retryAvailable.set(false);
    }
}
