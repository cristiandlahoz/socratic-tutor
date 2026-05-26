package com.wornux.ui.ingestion;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.services.chat.ConversationService;
import com.wornux.services.document.ApproveDocumentCommand;
import com.wornux.services.document.DocumentIngestionService;
import com.wornux.services.document.StartIngestionCommand;
import com.wornux.infrastructure.web.BrowserClientService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.chat.ChatState;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@SpringComponent
@RouteScope
@RouteScopeOwner(MainLayout.class)
public class DocumentIngestionUiController implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String DOCUMENT_QUERY_PARAMETER = "d";

    private final DocumentIngestionService documentIngestionService;
    private final BrowserClientService browserClientService;
    private final ConversationService conversationService;
    private final ChatState chatUiState;
    private final DocumentIngestionState state;
    private transient Disposable activeTask;
    private transient UploadedFile lastUploadedFile;

    public DocumentIngestionUiController(
            DocumentIngestionService documentIngestionService,
            BrowserClientService browserClientService,
            ConversationService conversationService,
            ChatState chatUiState,
            DocumentIngestionState state) {
        this.documentIngestionService = documentIngestionService;
        this.browserClientService = browserClientService;
        this.conversationService = conversationService;
        this.chatUiState = chatUiState;
        this.state = state;
    }

    public DocumentIngestionState state() {
        return state;
    }

    public void initializeFromRoute(String documentIdParam) {
        ensureClientContext();
        Optional<DocumentReviewViewModel> review = parseUuid(documentIdParam)
                .flatMap(documentId -> documentIngestionService.loadReview(chatUiState.clientId().peek(), documentId));
        if (review.isEmpty()) {
            review = documentIngestionService.loadLatestReview(chatUiState.clientId().peek());
        }
        review.ifPresent(state::apply);
    }

    public void uploadPdf(String fileName, String mimeType, byte[] content, UI ui) {
        abortActiveTask();
        ensureClientContext();
        lastUploadedFile = new UploadedFile(fileName, mimeType, content);
        state.startUploadProcessing(fileName, "Transformando y segmentando PDF con Docling.");

        activeTask = Mono
                .fromCallable(
                    () -> documentIngestionService.startIngestion(
                        new StartIngestionCommand(chatUiState.clientId().peek(), fileName, mimeType, content)))
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
        activeTask = Mono
                .fromCallable(
                    () -> documentIngestionService.approve(
                        new ApproveDocumentCommand(chatUiState.clientId().peek(),
                                state.activeDocumentId().peek(),
                                state.reviewedMarkdown().peek(),
                                state.segments().peek())))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    review -> runUi(ui, () -> applyReview(review)),
                    error -> runUi(ui, () -> state.markFailure(message(error), lastUploadedFile != null)));
    }

    public void updateMarkdown(String markdown) {
        state.reviewedMarkdown().set(markdown == null ? "" : markdown);
        state.dirty().set(true);
    }

    public void updateSegment(UUID segmentId, String content) {
        List<EditableSegmentViewModel> nextSegments = state.segments()
                .peek()
                .stream()
                .map(segment -> segment.id().equals(segmentId) ? segment.withContent(content) : segment)
                .toList();
        state.segments().set(nextSegments);
        state.dirty().set(true);
    }

    public void returnToChat() {
        if (chatUiState.activeConversationId().peek() != null) {
            UI.getCurrent().navigate("", QueryParameters.of("c", chatUiState.activeConversationId().peek().toString()));
            return;
        }
        UI.getCurrent().navigate("");
    }

    private void applyReview(DocumentReviewViewModel review) {
        state.apply(review);
        synchronizeAddressBar(review.documentId());
    }

    private void synchronizeAddressBar(UUID documentId) {
        UI.getCurrent()
                .getPage()
                .getHistory()
                .replaceState(
                    null,
                    new Location("documents", QueryParameters.of(DOCUMENT_QUERY_PARAMETER, documentId.toString())));
    }

    private void ensureClientContext() {
        if (chatUiState.clientId().peek() == null) {
            chatUiState.clientId().set(browserClientService.resolveClientId());
        }
        chatUiState.replaceConversationHistory(conversationService.listConversations(chatUiState.clientId().peek()));
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
        return throwable.getMessage();
    }

    private Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        }
        catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private record UploadedFile(String fileName, String mimeType, byte[] content) {}
}
