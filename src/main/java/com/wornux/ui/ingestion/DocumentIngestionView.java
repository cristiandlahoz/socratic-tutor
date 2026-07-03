package com.wornux.ui.ingestion;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.wornux.config.DocumentIngestionProperties;
import com.wornux.services.document.CourseMaterialCatalog;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.auth.NoAccessView;
import com.wornux.ui.components.ingestion.DocumentSegmentEditorList;
import com.wornux.ui.components.ingestion.DocumentStatusPanel;
import com.wornux.ui.css.UiCss;

import jakarta.annotation.security.PermitAll;

@Route(value = "documents", layout = MainLayout.class)
@PermitAll
@RequiresPermission(AppPermission.COURSE_MATERIAL_VIEW)
public class DocumentIngestionView extends Composite<Div> implements BeforeEnterObserver {

    private final DocumentIngestionUiController controller;
    private final DocumentStatusPanel statusPanel;
    private final DocumentSegmentEditorList segmentEditorList;
    private final TextArea markdownEditor;
    private final TextArea catalogUseWhenField;
    private final Button generateCatalogButton;
    private final Div generatedCatalogPreview;
    private final Button approveButton;
    private final Button retryButton;
    private final Button deleteButton;
    private final transient AuthenticatedAccountService authenticatedAccountService;
    private final transient WorkspaceRoutingService workspaceRoutingService;

    public DocumentIngestionView(
            @RouteScopeOwner(MainLayout.class) DocumentIngestionUiController controller,
            @RouteScopeOwner(MainLayout.class) DocumentIngestionState state,
            DocumentIngestionProperties properties,
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService) {
        this.controller = controller;
        this.authenticatedAccountService = authenticatedAccountService;
        this.workspaceRoutingService = workspaceRoutingService;

        var eyebrow = new Span("Document ETL");
        UiCss.DOCUMENT_INGEST_EYEBROW.addTo(eyebrow);

        var title = new H2("Ingestar información");
        UiCss.DOCUMENT_INGEST_TITLE.addTo(title);

        var description = new Paragraph(
                "Arrastra un PDF, deja que Docling lo transforme y segmente, valida los segmentos y luego indexalo para preguntas posteriores.");
        UiCss.DOCUMENT_INGEST_DESCRIPTION.addTo(description);

        Button backToConversationButton = new Button("Volver a la conversación");
        backToConversationButton.setId("document-ingestion-back-to-chat");
        backToConversationButton.addThemeVariants(ButtonVariant.TERTIARY);
        UiCss.DOCUMENT_INGEST_BACK_BUTTON.addTo(backToConversationButton);
        backToConversationButton.setIcon(new Icon(VaadinIcon.ARROW_LEFT));
        backToConversationButton.getThemeNames().remove("icon");
        backToConversationButton.addClickListener(_ -> controller.returnToConversation());

        var headerCopy = new Div(eyebrow, title, description);
        UiCss.DOCUMENT_INGEST_HEADER_COPY.addTo(headerCopy);

        var header = new HorizontalLayout(headerCopy, backToConversationButton);
        UiCss.DOCUMENT_INGEST_HEADER.addTo(header);
        header.setWidthFull();
        header.setPadding(false);
        header.setSpacing(true);

        Upload upload = createUpload(properties);
        var uploadCard = new Div(uploadCardIntro(), upload);
        uploadCard.setId("document-ingestion-upload-card");
        UiCss.DOCUMENT_INGEST_UPLOAD_CARD.addTo(uploadCard);

        statusPanel = new DocumentStatusPanel();

        var topGrid = new HorizontalLayout();
        topGrid.add(uploadCard, statusPanel);

        markdownEditor = new TextArea("Markdown revisado");
        markdownEditor.setId("document-ingestion-markdown-editor");
        markdownEditor.setWidthFull();
        markdownEditor.setMinHeight("22rem");
        markdownEditor.setMaxLength(200_000);
        markdownEditor.setValueChangeMode(ValueChangeMode.EAGER);
        UiCss.DOCUMENT_INGEST_MARKDOWN_EDITOR.addTo(markdownEditor);
        markdownEditor.addValueChangeListener(event -> controller.updateMarkdown(event.getValue()));

        var markdownShell = new Div(sectionHeader(
            "Fuente canonical",
            "Este markdown es el artefacto editable que después se segmenta e indexa."), markdownEditor);
        UiCss.DOCUMENT_INGEST_MARKDOWN_SHELL.addTo(markdownShell);

        catalogUseWhenField = createCatalogUseWhenField();
        generateCatalogButton = createGenerateCatalogButton();
        generatedCatalogPreview = createGeneratedCatalogPreview();
        var catalogShell = createCatalogShell(catalogUseWhenField, generateCatalogButton, generatedCatalogPreview);

        segmentEditorList = new DocumentSegmentEditorList();
        segmentEditorList.setSegmentChangeListener(controller::updateSegment);
        segmentEditorList.setSegmentDeleteListener(controller::deleteSegment);

        var segmentsShell = new Div(
                sectionHeader(
                    "segmentos",
                    "Docling HybridChunker crea estos segmentos con metadata de pagina, tokens y captions."),
                segmentEditorList);
        segmentsShell.setId("document-ingestion-segments-shell");
        UiCss.DOCUMENT_INGEST_SEGMENTS_SHELL.addTo(segmentsShell);

        approveButton = new Button("Aprobar e indexar");
        approveButton.setId("document-ingestion-approve-button");
        approveButton.addThemeVariants(ButtonVariant.PRIMARY);
        approveButton.setIcon(new Icon(VaadinIcon.DATABASE));
        approveButton.getThemeNames().remove("icon");
        UiCss.DOCUMENT_INGEST_APPROVE_BUTTON.addTo(approveButton);
        approveButton.addClickShortcut(Key.ENTER).listenOn(markdownEditor);
        approveButton.addClickListener(_ -> controller.approve(getUI().orElse(null)));

        retryButton = new Button("Reintentar");
        retryButton.setId("document-ingestion-retry-button");
        retryButton.addThemeVariants(ButtonVariant.TERTIARY);
        UiCss.DOCUMENT_INGEST_RETRY_BUTTON.addTo(retryButton);
        retryButton.addClickListener(_ -> controller.retry(getUI().orElse(null)));

        deleteButton = new Button("Eliminar documento");
        deleteButton.setId("document-ingestion-delete-button");
        deleteButton.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        deleteButton.addClickListener(_ -> controller.deleteCurrentDocument(getUI().orElse(null)));

        var actionBar = new Div(approveButton, retryButton, deleteButton);
        actionBar.setId("document-ingestion-action-bar");
        UiCss.DOCUMENT_INGEST_ACTION_BAR.addTo(actionBar);

        var details = new Details("Visualizar todo el contenido", markdownShell);
        var reviewStack = new Div(details, catalogShell, segmentsShell, actionBar);
        reviewStack.setId("document-ingestion-review-stack");
        UiCss.DOCUMENT_INGEST_REVIEW_STACK.addTo(reviewStack);

        var root = getContent();
        root.setId("document-ingestion-view");
        UiCss.DOCUMENT_INGEST_VIEW.addTo(root);
        root.add(header, topGrid, reviewStack);

        Signal.effect(
            statusPanel,
            () -> statusPanel.setStatus(
                state.fileName().get(),
                state.stageLabel().get(),
                state.busy().get(),
                state.indexed().get(),
                state.failureMessage().get()));
        Signal.effect(markdownEditor, () -> syncMarkdownEditor(state.reviewedMarkdown().get()));
        Signal.effect(catalogUseWhenField, () -> syncCatalogUseWhenField(state.catalogUseWhen().get()));
        Signal.effect(generatedCatalogPreview, () -> renderGeneratedCatalog(state.generatedCatalog().get()));
        Signal.effect(segmentEditorList, () -> segmentEditorList.setSegments(state.segments().get()));
        Signal.effect(reviewStack, () -> reviewStack.setVisible(state.reviewVisible().get()));
        Signal.effect(generateCatalogButton, () -> generateCatalogButton.setEnabled(state.canGenerateCatalog().get()));
        Signal.effect(approveButton, () -> approveButton.setEnabled(state.canApprove().get()));
        Signal.effect(retryButton, () -> retryButton.setVisible(state.retryAvailable().get()));
        Signal.effect(deleteButton, () -> deleteButton.setVisible(!state.indexedVectorIds().get().isEmpty()));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR)) {
            event.forwardTo(NoAccessView.class);
        }
    }

    private Upload createUpload(DocumentIngestionProperties properties) {
        Upload upload = new Upload(UploadHandler.inMemory(
            (metadata, data) -> controller
                    .uploadPdf(metadata.fileName(), metadata.contentType(), data, getUI().orElse(null))));
        upload.setId("document-ingestion-upload");
        upload.setDropAllowed(true);
        upload.setAcceptedFileTypes("application/pdf", ".pdf");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(properties.getMaxFileSizeBytes());
        upload.setWidthFull();
        upload.setI18n(createUploadI18n());
        upload.setUploadButton(createPrimaryUploadButton());
        upload.setDropLabel(new Span("Arrastra y suelta aquí el PDF o usa el selector."));
        upload.setDropLabelIcon(VaadinIcon.CLOUD_UPLOAD_O.create());
        upload.addFileRejectedListener(event -> {
            var notification = Notification.show(event.getErrorMessage(), 4_000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.ERROR);
        });
        return upload;
    }

    private Button createPrimaryUploadButton() {
        var button = new Button("Subir PDF");
        button.setId("document-ingestion-upload-button");
        button.addThemeVariants(ButtonVariant.PRIMARY);
        UiCss.DOCUMENT_INGEST_UPLOAD_BUTTON.addTo(button);
        return button;
    }

    private UploadI18N createUploadI18n() {
        UploadI18N i18n = new UploadI18N();
        i18n.setDropFiles(new UploadI18N.DropFiles().setOne("suelta el PDF aquí").setMany("suelta los PDFs aquí"));
        i18n.setAddFiles(new UploadI18N.AddFiles().setOne("Subir PDF").setMany("Subir PDFs"));
        i18n.setError(
            new UploadI18N.Error().setTooManyFiles("Solo se permite un PDF por vez.")
                    .setFileIsTooBig("El PDF supera el limite configurado.")
                    .setIncorrectFileType("El archivo debe ser un PDF."));
        i18n.setUploading(
            new UploadI18N.Uploading()
                    .setStatus(
                        new UploadI18N.Uploading.Status().setConnecting("Conectando...")
                                .setStalled("Pausado")
                                .setProcessing("Procesando archivo...")
                                .setHeld("En cola"))
                    .setRemainingTime(
                        new UploadI18N.Uploading.RemainingTime().setPrefix("tiempo restante: ")
                                .setUnknown("tiempo restante desconocido"))
                    .setError(
                        new UploadI18N.Uploading.Error().setServerUnavailable("La subida fallo, intenta otra vez.")
                                .setUnexpectedServerError("El servidor rechazo la subida.")
                                .setForbidden("No tienes permiso para subir este archivo.")));
        i18n.setUnits(new UploadI18N.Units().setSize(Arrays.asList("B", "kB", "MB", "GB", "TB")));
        return i18n;
    }

    private Div uploadCardIntro() {
        var title = new Span("PDF de entrada");
        UiCss.DOCUMENT_INGEST_UPLOAD_TITLE.addTo(title);

        var hint = new Paragraph(
                "Solo PDF por ahora. Validamos tipo, tamaño y firma básica del archivo antes de llamar a Docling.");
        UiCss.DOCUMENT_INGEST_UPLOAD_HINT.addTo(hint);

        var wrapper = new Div(title, hint);
        UiCss.DOCUMENT_INGEST_UPLOAD_COPY.addTo(wrapper);
        return wrapper;
    }

    private Div sectionHeader(String titleText, String descriptionText) {
        var title = new Span(titleText);
        UiCss.DOCUMENT_INGEST_SECTION_TITLE.addTo(title);

        var description = new Paragraph(descriptionText);
        UiCss.DOCUMENT_INGEST_SECTION_DESCRIPTION.addTo(description);

        var wrapper = new Div(title, description);
        UiCss.DOCUMENT_INGEST_SECTION_HEADER.addTo(wrapper);
        return wrapper;
    }

    private TextArea createCatalogUseWhenField() {
        var field = new TextArea("Cuándo debe usarlo el tutor");
        field.setId("document-ingestion-catalog-use-when");
        field.setWidthFull();
        field.setRequiredIndicatorVisible(true);
        field.setMaxLength(200);
        field.setValueChangeMode(ValueChangeMode.EAGER);
        field.setHelperText("Máximo 200 caracteres. Sé específico: tarea, tema, unidad, conceptos o situaciones donde este material aplica.");
        field.addValueChangeListener(event -> controller.updateCatalogUseWhen(event.getValue()));
        UiCss.DOCUMENT_INGEST_CATALOG_USE_WHEN.addTo(field);
        return field;
    }

    private Button createGenerateCatalogButton() {
        var button = new Button("Generar catálogo");
        button.setId("document-ingestion-generate-catalog-button");
        button.addThemeVariants(ButtonVariant.PRIMARY);
        button.addClickListener(_ -> controller.generateCatalog(getUI().orElse(null)));
        UiCss.DOCUMENT_INGEST_GENERATE_CATALOG_BUTTON.addTo(button);
        return button;
    }

    private Div createGeneratedCatalogPreview() {
        var preview = new Div();
        preview.setId("document-ingestion-generated-catalog-preview");
        UiCss.DOCUMENT_INGEST_GENERATED_CATALOG_PREVIEW.addTo(preview);
        preview.setVisible(false);
        return preview;
    }

    private Div createCatalogShell(TextArea field, Button generateButton, Div preview) {
        var shell = new Div(
                sectionHeader(
                    "criterio de uso",
                    "Esta descripción alimenta el catálogo que ayuda al tutor a decidir cuándo buscar este material."),
                field,
                generateButton,
                preview);
        shell.setId("document-ingestion-catalog-shell");
        UiCss.DOCUMENT_INGEST_CATALOG_SHELL.addTo(shell);
        return shell;
    }

    private void renderGeneratedCatalog(Optional<CourseMaterialCatalog> catalog) {
        generatedCatalogPreview.removeAll();
        generatedCatalogPreview.setVisible(catalog.isPresent());
        catalog.ifPresent(value -> generatedCatalogPreview.add(
            new Span(value.label()),
            new Paragraph(value.useWhen()),
            new Paragraph("Alias: %s".formatted(String.join(", ", value.aliases())))));
    }

    private void syncMarkdownEditor(String nextValue) {
        syncTextArea(markdownEditor, nextValue);
    }

    private void syncCatalogUseWhenField(String nextValue) {
        syncTextArea(catalogUseWhenField, nextValue);
    }

    private void syncTextArea(TextArea textArea, String nextValue) {
        String safeValue = nextValue == null ? "" : nextValue;
        if (!Objects.equals(textArea.getValue(), safeValue)) {
            textArea.setValue(safeValue);
        }
    }
}
