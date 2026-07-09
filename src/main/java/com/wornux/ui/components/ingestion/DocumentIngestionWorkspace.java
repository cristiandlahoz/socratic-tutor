package com.wornux.ui.components.ingestion;

import java.io.Serial;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.UploadHandler;
import com.wornux.config.ApplicationProperties;
import com.wornux.services.document.ApproveDocumentCommand;
import com.wornux.services.document.CourseMaterialCatalog;
import com.wornux.services.document.DocumentIngestionJobRegistry;
import com.wornux.services.document.DocumentWorkspaceDetail;
import com.wornux.services.document.DocumentWorkspaceService;
import com.wornux.services.document.StartIngestionCommand;
import com.wornux.ui.css.UiCss;
import com.wornux.ui.ingestion.EditableSegmentViewModel;

@Tag("document-ingestion-workspace")
@JsModule("./ingestion/dropzone.ts")
@JsModule("./ingestion/document-ingestion-workspace.ts")
public class DocumentIngestionWorkspace extends Component {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<EditableSegmentViewModel>> SEGMENT_LIST_TYPE = new TypeReference<>() {};

    private final DocumentWorkspaceService workspaceService;
    private final transient DocumentIngestionJobRegistry jobRegistry;
    private transient AutoCloseable jobSubscription;

    public DocumentIngestionWorkspace(
            DocumentWorkspaceService workspaceService,
            ApplicationProperties.DocumentIngest properties,
            DocumentIngestionJobRegistry jobRegistry) {
        this.workspaceService = workspaceService;
        this.jobRegistry = jobRegistry;
        setId("document-ingestion-workspace");
        UiCss.DOCUMENT_INGEST_VIEW.addTo(this);
        addAttachListener(event -> attachJobStream(event.getUI()));
        addDetachListener(_ -> detachJobStream());
        configureUpload(properties);
    }

    @ClientCallable
    public String loadDocuments() {
        return toJson(workspaceService.listDocuments());
    }

    @ClientCallable
    public String loadDocument(String ingestionId) {
        return toJson(workspaceService.loadDocument(ingestionId));
    }

    @ClientCallable
    public String generateCatalog(String ingestionId, String title, String useWhen, String markdown, String segmentsJson) {
        var segments = parseSegments(segmentsJson);
        var context = jobRegistry.requireProfessorContext();
        jobRegistry.startCatalog(context, ingestionId, title, useWhen, markdown, segments);
        return toJson(jobRegistry.snapshot(context));
    }

    @ClientCallable
    public String indexDraft(String ingestionId, String title, String catalogJson, String markdown, String segmentsJson) {
        var catalog = fromJson(catalogJson, CourseMaterialCatalog.class);
        var segments = parseSegments(segmentsJson);
        var context = jobRegistry.requireProfessorContext();
        jobRegistry.startIndex(context, new ApproveDocumentCommand(ingestionId, title, catalog, markdown, segments));
        return toJson(jobRegistry.snapshot(context));
    }

    @ClientCallable
    public String reindexDocument(String detailJson) {
        var detail = fromJson(detailJson, DocumentWorkspaceDetail.class);
        var context = jobRegistry.requireProfessorContext();
        jobRegistry.startReindex(context, detail);
        return toJson(jobRegistry.snapshot(context));
    }

    @ClientCallable
    public String updateDraft(String detailJson) {
        var detail = fromJson(detailJson, DocumentWorkspaceDetail.class);
        var context = jobRegistry.requireProfessorContext();
        jobRegistry.updateDraft(context, detail);
        return "{}";
    }

    @ClientCallable
    public String deleteDraft(String ingestionId) {
        var context = jobRegistry.requireProfessorContext();
        jobRegistry.deleteDraft(context, ingestionId);
        return toJson(jobRegistry.snapshot(context));
    }

    @ClientCallable
    public String deleteDocument(String ingestionId) {
        workspaceService.deleteDocument(ingestionId);
        return toJson(workspaceService.listDocuments());
    }

    private void configureUpload(ApplicationProperties.DocumentIngest properties) {
        var upload = new Upload(UploadHandler.inMemory((metadata, data) -> {
            var command = new StartIngestionCommand(metadata.fileName(), metadata.contentType(), data);
            jobRegistry.startIngestion(jobRegistry.requireProfessorContext(), command);
        }));
        upload.setId("document-ingestion-upload");
        upload.setDropAllowed(true);
        upload.setAcceptedFileTypes("application/pdf", ".pdf");
        upload.setMaxFiles(10);
        upload.setMaxFileSize(properties.getMaxFileSizeBytes());
        upload.setWidthFull();
        upload.setI18n(createUploadI18n());
        upload.setUploadButton(createPrimaryUploadButton());
        upload.setDropLabel(new Span(""));
        upload.addFileRejectedListener(event -> {
            var notification = Notification.show(event.getErrorMessage(), 4_000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.ERROR);
        });
        var dropzone = new Element("document-upload-dropzone");
        dropzone.setAttribute("slot", "upload");
        dropzone.appendChild(upload.getElement());
        getElement().appendChild(dropzone);
    }

    private Button createPrimaryUploadButton() {
        var button = new Button("Seleccionar PDFs");
        button.setId("document-ingestion-upload-button");
        button.addThemeVariants(ButtonVariant.PRIMARY);
        UiCss.DOCUMENT_INGEST_UPLOAD_BUTTON.addTo(button);
        return button;
    }

    private UploadI18N createUploadI18n() {
        var i18n = new UploadI18N();
        i18n.setDropFiles(new UploadI18N.DropFiles().setOne("suelta el PDF aquí").setMany("suelta los PDFs aquí"));
        i18n.setAddFiles(new UploadI18N.AddFiles().setOne("Añadir PDF").setMany("Añadir PDFs"));
        i18n.setError(
            new UploadI18N.Error().setTooManyFiles("Selecciona menos PDFs para esta tanda.")
                    .setFileIsTooBig("El PDF supera el límite configurado.")
                    .setIncorrectFileType("El archivo debe ser un PDF."));
        i18n.setUploading(
            new UploadI18N.Uploading()
                    .setStatus(
                        new UploadI18N.Uploading.Status().setConnecting("Conectando...")
                                .setStalled("Pausado")
                                .setProcessing("Transformando y segmentando PDF con Docling...")
                                .setHeld("En cola de transformación"))
                    .setRemainingTime(
                        new UploadI18N.Uploading.RemainingTime().setPrefix("tiempo restante: ")
                                .setUnknown("tiempo restante desconocido"))
                    .setError(
                        new UploadI18N.Uploading.Error().setServerUnavailable("La subida falló, intenta otra vez.")
                                .setUnexpectedServerError("El servidor rechazó la subida.")
                                .setForbidden("No tienes permiso para subir este archivo.")));
        i18n.setUnits(new UploadI18N.Units().setSize(List.of("B", "kB", "MB", "GB", "TB")));
        return i18n;
    }

    private void attachJobStream(UI ui) {
        detachJobStream();
        var context = jobRegistry.requireProfessorContext();
        var session = ui.getSession();
        jobSubscription = jobRegistry.subscribe(
            context,
            snapshot -> runUiSideEffect(ui, session, () -> pushJobSnapshot(snapshot)));
    }

    private void detachJobStream() {
        if (jobSubscription == null) {
            return;
        }
        try {
            jobSubscription.close();
        }
        catch (Exception _) {
            // Best-effort UI subscription cleanup.
        }
        jobSubscription = null;
    }

    private void pushJobSnapshot(DocumentIngestionJobRegistry.DocumentJobSnapshot snapshot) {
        runClient("this.applyJobSnapshot($0)", toJson(snapshot));
    }

    private List<EditableSegmentViewModel> parseSegments(String segmentsJson) {
        if (segmentsJson == null || segmentsJson.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(segmentsJson, SEGMENT_LIST_TYPE);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("No se pudieron leer los segmentos del documento.", exception);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("No se pudo leer la información del documento.", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar la información del documento.", exception);
        }
    }

    private void runClient(String expression, Object... arguments) {
        getElement().executeJs(expression, arguments);
    }

    private void runUiSideEffect(UI ui, VaadinSession session, Runnable callback) {
        if (ui != null && ui.isAttached()) {
            ui.access(callback::run);
            return;
        }
        if (session != null) {
            session.access(callback::run);
            return;
        }
        callback.run();
    }
}
