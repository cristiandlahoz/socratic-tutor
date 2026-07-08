package com.wornux.services.document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.ui.ingestion.DocumentReviewViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class DocumentIngestionJobRegistry {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionJobRegistry.class);

    private final DocumentIngestionService ingestionService;
    private final DocumentWorkspaceService workspaceService;
    private final ActiveAcademicContextResolver contextResolver;
    private final Executor documentIngestionExecutor;
    private final Map<UUID, WorkspaceJobs> workspaces = new ConcurrentHashMap<>();

    public DocumentIngestionJobRegistry(
            DocumentIngestionService ingestionService,
            DocumentWorkspaceService workspaceService,
            ActiveAcademicContextResolver contextResolver,
            @Qualifier("documentIngestionExecutor") Executor documentIngestionExecutor) {
        this.ingestionService = ingestionService;
        this.workspaceService = workspaceService;
        this.contextResolver = contextResolver;
        this.documentIngestionExecutor = documentIngestionExecutor;
    }

    public ActiveAcademicContext requireProfessorContext() {
        var context = contextResolver.requireCurrent();
        if (context.groupClassKind() != GroupClassMemberKind.PROFESSOR) {
            throw new SetupRequiredException("An active professor class context is required for grounding uploads.");
        }
        return context;
    }

    public AutoCloseable subscribe(ActiveAcademicContext context, Consumer<DocumentJobSnapshot> listener) {
        var workspace = workspace(context);
        workspace.subscribers.add(listener);
        publishToListener(workspace, listener, workspace.snapshot(false, null));
        return () -> unsubscribe(workspace, listener);
    }

    public DocumentJobSnapshot snapshot(ActiveAcademicContext context) {
        return workspace(context).snapshot(false, null);
    }

    public void updateDraft(ActiveAcademicContext context, DocumentWorkspaceDetail detail) {
        if (detail == null || detail.ingestionId() == null || detail.ingestionId().isBlank()) {
            return;
        }
        workspace(context).putDraft(detail);
    }

    public void deleteDraft(ActiveAcademicContext context, String ingestionId) {
        if (ingestionId == null || ingestionId.isBlank()) {
            return;
        }
        var workspace = workspace(context);
        workspace.removeDraft(ingestionId);
        workspace.broadcast(false, null);
    }

    public void startIngestion(ActiveAcademicContext context, StartIngestionCommand command) {
        var workspace = workspace(context);
        workspace.startJob("Transformando %s en segundo plano...".formatted(command.originalFilename()));
        CompletableFuture
                .supplyAsync(() -> ingestionService.startIngestion(command, context), documentIngestionExecutor)
                .whenComplete((review, exception) -> {
                    if (exception != null) {
                        workspace.finishJobFailure(userMessage(exception));
                        return;
                    }
                    var detail = toWorkspaceDetail(review, null);
                    workspace.putDraft(detail);
                    workspace.finishJobSuccess("PDF transformado. Revisa los segmentos antes de indexar.", false, detail);
                });
    }

    public void startCatalog(
            ActiveAcademicContext context,
            String ingestionId,
            String title,
            String useWhen,
            String markdown,
            List<com.wornux.ui.ingestion.EditableSegmentViewModel> segments) {
        var workspace = workspace(context);
        var current = new DocumentWorkspaceDetail(
            ingestionId,
            title,
            "REVIEW_READY",
            null,
            markdown,
            segments == null ? List.of() : segments,
            List.of());
        workspace.putDraft(current);
        workspace.startJob("Generando catálogo de búsqueda...");
        CompletableFuture
                .supplyAsync(
                    () -> ingestionService.generateCatalog(title, useWhen, current.segments(), context),
                    documentIngestionExecutor)
                .whenComplete((catalog, exception) -> {
                    if (exception != null) {
                        workspace.finishJobFailure(userMessage(exception));
                        return;
                    }
                    var updated = new DocumentWorkspaceDetail(
                        ingestionId,
                        title,
                        "REVIEW_READY",
                        catalog,
                        markdown,
                        current.segments(),
                        List.of());
                    workspace.putDraft(updated);
                    workspace.finishJobSuccess("Catálogo generado. Ya puedes indexar el documento.", false, updated);
                });
    }

    public void startIndex(ActiveAcademicContext context, ApproveDocumentCommand command) {
        var workspace = workspace(context);
        workspace.startJob("Indexando segmentos para el tutor...");
        CompletableFuture
                .supplyAsync(() -> ingestionService.approve(command, context), documentIngestionExecutor)
                .whenComplete((review, exception) -> {
                    if (exception != null) {
                        workspace.finishJobFailure(userMessage(exception));
                        return;
                    }
                    var detail = toWorkspaceDetail(review, command.catalog());
                    workspace.removeDraft(command.ingestionId());
                    workspace.finishJobSuccess("Documento indexado para el tutor.", true, detail);
                });
    }

    public void startReindex(ActiveAcademicContext context, DocumentWorkspaceDetail detail) {
        var workspace = workspace(context);
        workspace.startJob("Reindexando documento para el tutor...");
        CompletableFuture
                .supplyAsync(() -> workspaceService.reindex(detail, context), documentIngestionExecutor)
                .whenComplete((updated, exception) -> {
                    if (exception != null) {
                        workspace.finishJobFailure(userMessage(exception));
                        return;
                    }
                    workspace.finishJobSuccess("Documento reindexado para el tutor.", true, updated);
                });
    }

    private void unsubscribe(WorkspaceJobs workspace, Consumer<DocumentJobSnapshot> listener) {
        workspace.subscribers.remove(listener);
    }

    private WorkspaceJobs workspace(ActiveAcademicContext context) {
        return workspaces.computeIfAbsent(context.groupClassId(), _ -> new WorkspaceJobs());
    }

    private void publishToListener(
            WorkspaceJobs workspace,
            Consumer<DocumentJobSnapshot> listener,
            DocumentJobSnapshot snapshot) {
        try {
            listener.accept(snapshot);
        }
        catch (Throwable exception) {
            unsubscribe(workspace, listener);
            log.debug(
                "document_ingestion_listener_removed failure_type={} failure_message={}",
                exception.getClass().getSimpleName(),
                exception.getMessage());
        }
    }

    private static DocumentWorkspaceDetail toWorkspaceDetail(
            DocumentReviewViewModel review,
            CourseMaterialCatalog catalog) {
        return new DocumentWorkspaceDetail(
            review.ingestionId(),
            review.filename(),
            review.indexed() ? "INDEXED" : "REVIEW_READY",
            catalog,
            review.markdown(),
            review.segments(),
            review.vectorIds());
    }

    private static String userMessage(Throwable exception) {
        var cause = exception;
        if (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? "Ocurrió un error inesperado."
                : cause.getMessage();
    }

    public record DocumentJobSnapshot(
            List<DocumentWorkspaceDetail> drafts,
            boolean busy,
            int activeJobCount,
            String statusMessage,
            String errorMessage,
            boolean reloadDocuments,
            DocumentWorkspaceDetail selectedDetail) {}

    private final class WorkspaceJobs {

        private final Map<String, DocumentWorkspaceDetail> drafts = new LinkedHashMap<>();
        private final AtomicInteger activeJobCount = new AtomicInteger();
        private final CopyOnWriteArrayList<Consumer<DocumentJobSnapshot>> subscribers = new CopyOnWriteArrayList<>();
        private String statusMessage = "";
        private String errorMessage = "";

        private synchronized void putDraft(DocumentWorkspaceDetail detail) {
            drafts.put(detail.ingestionId(), detail);
        }

        private synchronized void removeDraft(String ingestionId) {
            drafts.remove(ingestionId);
        }

        private void startJob(String message) {
            synchronized (this) {
                activeJobCount.incrementAndGet();
                statusMessage = message;
                errorMessage = "";
            }
            broadcast(false, null);
        }

        private void finishJobSuccess(String message, boolean reloadDocuments, DocumentWorkspaceDetail selectedDetail) {
            synchronized (this) {
                activeJobCount.updateAndGet(value -> Math.max(0, value - 1));
                statusMessage = activeJobCount.get() > 0
                        ? "%d trabajo%s en ejecución...".formatted(
                            activeJobCount.get(),
                            activeJobCount.get() == 1 ? "" : "s")
                        : message;
                errorMessage = "";
            }
            broadcast(reloadDocuments, selectedDetail);
        }

        private void finishJobFailure(String message) {
            synchronized (this) {
                activeJobCount.updateAndGet(value -> Math.max(0, value - 1));
                statusMessage = activeJobCount.get() > 0
                        ? "%d trabajo%s en ejecución...".formatted(
                            activeJobCount.get(),
                            activeJobCount.get() == 1 ? "" : "s")
                        : "";
                errorMessage = message;
            }
            broadcast(false, null);
        }

        private DocumentJobSnapshot snapshot(boolean reloadDocuments, DocumentWorkspaceDetail selectedDetail) {
            synchronized (this) {
                return new DocumentJobSnapshot(
                    sortedDrafts(),
                    activeJobCount.get() > 0,
                    activeJobCount.get(),
                    statusMessage,
                    errorMessage,
                    reloadDocuments,
                    selectedDetail);
            }
        }

        private List<DocumentWorkspaceDetail> sortedDrafts() {
            var values = new ArrayList<>(drafts.values());
            values.sort(Comparator.comparing(
                detail -> detail.title() == null ? "" : detail.title(),
                String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(values);
        }

        private void broadcast(boolean reloadDocuments, DocumentWorkspaceDetail selectedDetail) {
            var snapshot = snapshot(reloadDocuments, selectedDetail);
            subscribers.forEach(listener -> publishToListener(this, listener, snapshot));
        }
    }
}
