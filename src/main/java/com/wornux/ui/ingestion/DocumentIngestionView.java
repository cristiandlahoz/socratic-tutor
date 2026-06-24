package com.wornux.ui.ingestion;

import java.util.Arrays;
import java.util.Objects;

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
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.components.ShellDrawerToggle;
import com.wornux.ui.components.ingestion.DocumentSegmentEditorList;
import com.wornux.ui.components.ingestion.DocumentStatusPanel;

@Route(value = "documents", layout = MainLayout.class)
public class DocumentIngestionView extends Composite<Div> implements BeforeEnterObserver {

    private final DocumentIngestionUiController controller;
    private final DocumentStatusPanel statusPanel;
    private final DocumentSegmentEditorList segmentEditorList;
    private final TextArea markdownEditor;
    private final Button approveButton;
    private final Button retryButton;
    private final Button deleteButton;
    private final AuthenticatedAccountService authenticatedAccountService;
    private final WorkspaceRoutingService workspaceRoutingService;

    public DocumentIngestionView(
            @RouteScopeOwner(MainLayout.class) DocumentIngestionUiController controller,
            @RouteScopeOwner(MainLayout.class) DocumentIngestionState state,
            DocumentIngestionProperties properties,
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService) {
        this.controller = controller;
        this.authenticatedAccountService = authenticatedAccountService;
        this.workspaceRoutingService = workspaceRoutingService;

        var drawerToggle = new ShellDrawerToggle("shell-drawer-toggle", "Abrir menu");

        var eyebrow = new Span("Document ETL");
        eyebrow.addClassName("document-ingest-eyebrow");

        var title = new H2("Ingestar información");
        title.addClassName("document-ingest-title");

        var description = new Paragraph(
                "Arrastra un PDF, deja que Docling lo transforme y segmente, valida los segmentos y luego indexalo para preguntas posteriores.");
        description.addClassName("document-ingest-description");

        Button backToChatButton = new Button("Volver a la conversación");
        backToChatButton.setId("document-ingestion-back-to-chat");
        backToChatButton.addThemeVariants(ButtonVariant.TERTIARY);
        backToChatButton.addClassName("document-ingest-back-button");
        backToChatButton.setIcon(new Icon(VaadinIcon.ARROW_LEFT));
        backToChatButton.getThemeNames().remove("icon");
        backToChatButton.addClickListener(_ -> controller.returnToChat());

        var headerCopy = new Div(eyebrow, title, description);
        headerCopy.addClassName("document-ingest-header-copy");

        var header = new HorizontalLayout(headerCopy, backToChatButton);
        header.addClassName("document-ingest-header");
        header.setWidthFull();
        header.setPadding(false);
        header.setSpacing(true);

        Upload upload = createUpload(properties);
        var uploadCard = new Div(uploadCardIntro(), upload);
        uploadCard.setId("document-ingestion-upload-card");
        uploadCard.addClassName("document-ingest-upload-card");

        statusPanel = new DocumentStatusPanel();

        var topGrid = new HorizontalLayout();
        topGrid.add(uploadCard, statusPanel);

        markdownEditor = new TextArea("Markdown revisado");
        markdownEditor.setId("document-ingestion-markdown-editor");
        markdownEditor.setWidthFull();
        markdownEditor.setMinHeight("22rem");
        markdownEditor.setMaxLength(200_000);
        markdownEditor.setValueChangeMode(ValueChangeMode.EAGER);
        markdownEditor.addClassName("document-ingest-markdown-editor");
        markdownEditor.addValueChangeListener(event -> controller.updateMarkdown(event.getValue()));

        var markdownShell = new Div(sectionHeader(
            "Fuente canonical",
            "Este markdown es el artefacto editable que después se segmenta e indexa."), markdownEditor);
        markdownShell.addClassName("document-ingest-markdown-shell");

        segmentEditorList = new DocumentSegmentEditorList();
        segmentEditorList.setSegmentChangeListener(controller::updateSegment);
        segmentEditorList.setSegmentDeleteListener(controller::deleteSegment);

        var segmentsShell = new Div(
                sectionHeader(
                    "segmentos",
                    "Docling HybridChunker crea estos segmentos con metadata de pagina, tokens y captions."),
                segmentEditorList);
        segmentsShell.setId("document-ingestion-segments-shell");
        segmentsShell.addClassName("document-ingest-segments-shell");

        approveButton = new Button("Aprobar e indexar");
        approveButton.setId("document-ingestion-approve-button");
        approveButton.addThemeVariants(ButtonVariant.PRIMARY);
        approveButton.setIcon(new Icon(VaadinIcon.DATABASE));
        approveButton.getThemeNames().remove("icon");
        approveButton.addClassName("document-ingest-approve-button");
        approveButton.addClickShortcut(Key.ENTER).listenOn(markdownEditor);
        approveButton.addClickListener(_ -> controller.approve(getUI().orElse(null)));

        retryButton = new Button("Reintentar");
        retryButton.setId("document-ingestion-retry-button");
        retryButton.addThemeVariants(ButtonVariant.TERTIARY);
        retryButton.addClassName("document-ingest-retry-button");
        retryButton.addClickListener(_ -> controller.retry(getUI().orElse(null)));

        deleteButton = new Button("Eliminar documento");
        deleteButton.setId("document-ingestion-delete-button");
        deleteButton.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        deleteButton.addClickListener(_ -> controller.deleteCurrentDocument(getUI().orElse(null)));

        var actionBar = new Div(approveButton, retryButton, deleteButton);
        actionBar.setId("document-ingestion-action-bar");
        actionBar.addClassName("document-ingest-action-bar");

        var details = new Details("Visualizar todo el contenido", markdownShell);
        var reviewStack = new Div(details, segmentsShell, actionBar);
        reviewStack.setId("document-ingestion-review-stack");
        reviewStack.addClassName("document-ingest-review-stack");

        var root = getContent();
        root.setId("document-ingestion-view");
        root.addClassName("document-ingest-view");
        root.add(drawerToggle, header, topGrid, reviewStack);

        Signal.effect(
            statusPanel,
            () -> statusPanel.setStatus(
                state.fileName().get(),
                state.stageLabel().get(),
                state.busy().get(),
                state.indexed().get(),
                state.failureMessage().get()));
        Signal.effect(markdownEditor, () -> syncMarkdownEditor(state.reviewedMarkdown().get()));
        Signal.effect(segmentEditorList, () -> segmentEditorList.setSegments(state.segments().get()));
        Signal.effect(reviewStack, () -> reviewStack.setVisible(state.reviewVisible().get()));
        Signal.effect(approveButton, () -> approveButton.setEnabled(state.canApprove().get()));
        Signal.effect(retryButton, () -> retryButton.setVisible(state.retryAvailable().get()));
        Signal.effect(deleteButton, () -> deleteButton.setVisible(!state.indexedVectorIds().get().isEmpty()));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR)) {
            event.forwardTo("no-access");
            return;
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
        button.addClassName("document-ingest-upload-button");
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
        title.addClassName("document-ingest-upload-title");

        var hint = new Paragraph(
                "Solo PDF por ahora. Validamos tipo, tamaño y firma básica del archivo antes de llamar a Docling.");
        hint.addClassName("document-ingest-upload-hint");

        var wrapper = new Div(title, hint);
        wrapper.addClassName("document-ingest-upload-copy");
        return wrapper;
    }

    private Div sectionHeader(String titleText, String descriptionText) {
        var title = new Span(titleText);
        title.addClassName("document-ingest-section-title");

        var description = new Paragraph(descriptionText);
        description.addClassName("document-ingest-section-description");

        var wrapper = new Div(title, description);
        wrapper.addClassName("document-ingest-section-header");
        return wrapper;
    }

    private void syncMarkdownEditor(String nextValue) {
        String safeValue = nextValue == null ? "" : nextValue;
        if (!Objects.equals(markdownEditor.getValue(), safeValue)) {
            markdownEditor.setValue(safeValue);
        }
    }
}
