package com.wornux.documentingest.ui;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.MainLayout;
import com.wornux.documentingest.DocumentReviewVm;
import com.wornux.documentingest.EditableSegmentVm;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@SpringComponent
@RouteScope
@RouteScopeOwner(MainLayout.class)
public class DocumentIngestionUiState implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private final ValueSignal<UUID> activeDocumentId = new ValueSignal<>(null);
  private final ValueSignal<UUID> activeJobId = new ValueSignal<>(null);
  private final ValueSignal<String> fileName = new ValueSignal<>("");
  private final ValueSignal<String> stageLabel = new ValueSignal<>("Sube un PDF para comenzar.");
  private final ValueSignal<String> failureMessage = new ValueSignal<>("");
  private final ValueSignal<String> reviewedMarkdown = new ValueSignal<>("");
  private final ValueSignal<List<EditableSegmentVm>> segments = new ValueSignal<>(List.of());
  private final ValueSignal<Boolean> busy = new ValueSignal<>(false);
  private final ValueSignal<Boolean> indexed = new ValueSignal<>(false);
  private final ValueSignal<Boolean> dirty = new ValueSignal<>(false);
  private final ValueSignal<Boolean> retryAvailable = new ValueSignal<>(false);

  private final Signal<Boolean> reviewVisible =
      Signal.computed(() -> activeDocumentId.get() != null);
  private final Signal<Boolean> canApprove =
      Signal.computed(
          () ->
              !busy.get()
                  && activeDocumentId.get() != null
                  && !indexed.get()
                  && !reviewedMarkdown.get().isBlank()
                  && !segments.get().isEmpty()
                  && segments.get().stream()
                      .allMatch(
                          segment -> segment.content() != null && !segment.content().isBlank()));

  public ValueSignal<UUID> activeDocumentId() {
    return activeDocumentId;
  }

  public ValueSignal<UUID> activeJobId() {
    return activeJobId;
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

  public ValueSignal<List<EditableSegmentVm>> segments() {
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
    activeDocumentId.set(null);
    activeJobId.set(null);
    reviewedMarkdown.set("");
    segments.set(List.of());
    indexed.set(false);
    dirty.set(false);
    startProcessing(fileName, stageLabel);
  }

  public void apply(DocumentReviewVm reviewVm) {
    activeDocumentId.set(reviewVm.documentId());
    activeJobId.set(reviewVm.jobId());
    fileName.set(reviewVm.filename());
    stageLabel.set(reviewVm.stageLabel());
    failureMessage.set("");
    reviewedMarkdown.set(reviewVm.markdown());
    segments.set(List.copyOf(reviewVm.segments()));
    busy.set(false);
    indexed.set(reviewVm.indexed());
    dirty.set(false);
    retryAvailable.set(false);
  }

  public void markFailure(String message, boolean retryAvailable) {
    this.failureMessage.set(message == null ? "Ocurrio un error inesperado." : message);
    this.stageLabel.set("La ingestion fallo.");
    this.busy.set(false);
    this.retryAvailable.set(retryAvailable);
  }
}
