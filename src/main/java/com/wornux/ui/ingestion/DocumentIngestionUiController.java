package com.wornux.ui.ingestion;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.services.document.ApproveDocumentCommand;
import com.wornux.services.document.CourseMaterialCatalog;
import com.wornux.services.document.DocumentIngestionService;
import com.wornux.services.document.StartIngestionCommand;
import com.wornux.ui.MainLayout;
import com.wornux.ui.conversation.ConversationState;
import com.wornux.ui.conversation.ConversationView;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@SpringComponent
@RouteScope
@RouteScopeOwner(MainLayout.class)
@Slf4j
public class DocumentIngestionUiController implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DocumentIngestionService documentIngestionService;
    private final ConversationState conversationUiState;
    private final DocumentIngestionState state;
    private transient Disposable activeTask;
    private transient UploadedFile lastUploadedFile;

    public DocumentIngestionUiController(
            DocumentIngestionService documentIngestionService,
            @RouteScopeOwner(MainLayout.class) ConversationState conversationUiState,
            @RouteScopeOwner(MainLayout.class) DocumentIngestionState state) {
        this.documentIngestionService = documentIngestionService;
        this.conversationUiState = conversationUiState;
        this.state = state;
    }

    public DocumentIngestionState state() {
        return state;
    }

    public void uploadPdf(String fileName, String mimeType, byte[] content, UI ui) {
        abortActiveTask();
        lastUploadedFile = new UploadedFile(fileName, mimeType, content);
        state.startUploadProcessing(fileName, "Transformando y segmentando PDF con Docling.");

        activeTask = Mono.fromCallable(
            () -> documentIngestionService.startIngestion(new StartIngestionCommand(fileName, mimeType, content)))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    review -> runUi(ui, () -> applyReview(review)),
                    error -> runUi(ui, () -> state.markFailure(message(error), lastUploadedFile != null)));
    }

    public void retry(UI ui) {
        if (lastUploadedFile == null) {
            return;
        }
        uploadPdf(lastUploadedFile.fileName(), lastUploadedFile.mimeType(), lastUploadedFile.content(), ui);
    }

    public void approve(UI ui) {
        if (!state.canApprove().peek()) {
            return;
        }
        abortActiveTask();
        state.startProcessing(state.fileName().peek(), "Indexando segmentos para chat.");
        activeTask = Mono.fromCallable(() -> documentIngestionService.approve(approveCommand()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    review -> runUi(ui, () -> applyReview(review)),
                    error -> runUi(ui, () -> state.markFailure(message(error), lastUploadedFile != null)));
    }

    public void deleteCurrentDocument(UI ui) {
        if (state.indexedVectorIds().peek().isEmpty()) {
            return;
        }
        abortActiveTask();
        List<String> vectorIds = state.indexedVectorIds().peek();
        state.startProcessing(state.fileName().peek(), "Eliminando documento indexado.");
        activeTask = Mono.fromCallable(() -> {
            documentIngestionService.delete(vectorIds);
            return true;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    _ -> runUi(ui, state::reset),
                    error -> runUi(ui, () -> state.markFailure(message(error), false)));
    }

    public void updateMarkdown(String markdown) {
        updateReviewedText(markdown);
    }

    public void updateCatalogUseWhen(String catalogUseWhen) {
        updateCatalogDescription(catalogUseWhen);
    }

    public void generateCatalog(UI ui) {
        abortActiveTask();
        state.startProcessing(state.fileName().peek(), "Generando catálogo de búsqueda.");
        activeTask = Mono.fromCallable(
            () -> documentIngestionService
                    .generateCatalog(state.fileName().peek(), state.catalogUseWhen().peek(), state.segments().peek()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    catalog -> runUi(ui, () -> applyGeneratedCatalog(catalog)),
                    error -> runUi(ui, () -> state.markFailure(message(error), lastUploadedFile != null)));
    }

    public void updateSegment(String segmentId, String content) {
        List<EditableSegmentViewModel> nextSegments = state.segments()
                .peek()
                .stream()
                .map(segment -> segment.id().equals(segmentId) ? segment.withContent(content) : segment)
                .toList();
        updateSegments(nextSegments);
    }

    public void deleteSegment(String segmentId) {
        if (segmentId == null) {
            return;
        }
        List<EditableSegmentViewModel> nextSegments =
                state.segments().peek().stream().filter(segment -> !segment.id().equals(segmentId)).toList();
        if (nextSegments.size() == state.segments().peek().size()) {
            return;
        }
        updateSegments(nextSegments);
    }

    public void returnToConversation() {
        if (conversationUiState.activeConversationId().peek() != null) {
            UI.getCurrent()
                    .navigate(
                        ConversationView.class,
                        QueryParameters.of("c", conversationUiState.activeConversationId().peek().toString()));
            return;
        }
        UI.getCurrent().navigate(ConversationView.class);
    }

    private ApproveDocumentCommand approveCommand() {
        return new ApproveDocumentCommand(state.activeIngestionId().peek(),
                state.fileName().peek(),
                state.generatedCatalog().peek().orElseThrow(),
                state.reviewedMarkdown().peek(),
                state.segments().peek());
    }

    private void updateReviewedText(String markdown) {
        state.reviewedMarkdown().set(safeText(markdown));
        state.dirty().set(true);
    }

    private void updateCatalogDescription(String catalogUseWhen) {
        state.catalogUseWhen().set(safeText(catalogUseWhen));
        invalidateCatalog();
    }

    private void updateSegments(List<EditableSegmentViewModel> segments) {
        state.segments().set(segments);
        invalidateCatalog();
    }

    private void invalidateCatalog() {
        state.generatedCatalog().set(Optional.empty());
        state.dirty().set(true);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private void applyGeneratedCatalog(CourseMaterialCatalog catalog) {
        state.generatedCatalog().set(Optional.of(catalog));
        state.busy().set(false);
        state.stageLabel().set("Catálogo generado. Revisa y aprueba el indexado.");
        state.failureMessage().set("");
    }

    private void applyReview(DocumentReviewViewModel review) {
        state.apply(review);
    }

    private void abortActiveTask() {
        if (activeTask != null) {
            activeTask.dispose();
            activeTask = null;
        }
    }

    private void runUi(UI ui, Runnable runnable) {
        if (ui == null) {
            runnable.run();
            return;
        }
        ui.access(runnable::run);
    }

    private String message(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "Ocurrió un error inesperado.";
        }
        log.error(throwable.getMessage());
        return throwable.getMessage();
    }

    private record UploadedFile(String fileName, String mimeType, byte[] content) {}
}
