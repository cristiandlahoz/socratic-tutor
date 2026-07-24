package com.wornux.ui.training_activity;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.services.training_activity.instruction_review.InstructionQualityReviewException;
import com.wornux.services.training_activity.instruction_review.InstructionReviewSnapshotDto;
import com.wornux.services.training_activity.instruction_review.InstructionReviewUnavailableException;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.services.training_activity.TrainingActivitySaveCommand;
import com.wornux.services.training_activity.TrainingActivityReportProjectionService;
import com.wornux.ui.training_activity.instruction_review.InstructionLinterEditor;
import com.wornux.ui.conversation.MessagesList;
import com.wornux.ui.conversation.MessageItem;
import com.wornux.ui.css.UiCss;

public class TrainingActivityDialog extends Div {

    private static final DateTimeFormatter REPORT_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd MMM yyyy · HH:mm", Locale.of("es", "DO"));

    private final transient TrainingActivity original;
    private final transient TrainingActivityService trainingActivityService;
    private final transient SafeBrowserModeService safeBrowserModeService;
    private final transient SafeBrowserAssignmentStateBus assignmentStateBus;
    private final transient TrainingActivityReportProjectionService reportProjectionService;
    private final transient Consumer<TrainingActivity> onSave;
    private final transient Runnable onClose;
    private transient TrainingActivity activitySnapshot;
    private transient java.util.UUID displayedReportAssignmentId;

    private final Div panel = new Div();
    private final TextField titleField;
    private final InstructionLinterEditor instructionField;
    private final Checkbox safeBrowserField;
    private final Button saveButton = new Button("Guardar cambios", _ -> onSaveClick());
    private AutoCloseable assignmentStateSubscription;
    private boolean activityMode = true;
    private transient InstructionReviewSnapshotDto displayedReviewSnapshot;
    private String displayedReviewTitle = "";
    private String displayedReviewInstructions = "";
    private final UUID reviewCandidateId = UUID.randomUUID();

    public TrainingActivityDialog(
            TrainingActivity activity,
            TrainingActivityService trainingActivityService,
            SafeBrowserModeService safeBrowserModeService,
            SafeBrowserAssignmentStateBus assignmentStateBus,
            TrainingActivityReportProjectionService reportProjectionService,
            Consumer<TrainingActivity> onSave,
            Runnable onClose) {
        this.original = activity;
        this.trainingActivityService = trainingActivityService;
        this.safeBrowserModeService = safeBrowserModeService;
        this.assignmentStateBus = assignmentStateBus;
        this.reportProjectionService = Objects.requireNonNull(reportProjectionService, "reportProjectionService cannot be null");
        this.onSave = onSave;
        this.onClose = onClose;
        this.activitySnapshot = activity;

        UiCss.TRAINING_ACTIVITY_OVERLAY.addTo(this);

        var backdrop = new Div();
        UiCss.TRAINING_ACTIVITY_OVERLAY_BACKDROP.addTo(backdrop);
        backdrop.addClickListener(_ -> close());

        UiCss.TRAINING_ACTIVITY_OVERLAY_PANEL.addTo(panel);

        titleField = new TextField("Título");
        titleField.setWidthFull();
        titleField.setValue(activity.getTitle());
        titleField.addValueChangeListener(_ -> invalidateDisplayedReviewConfirmation());

        instructionField = new InstructionLinterEditor();
        instructionField.setWidthFull();
        instructionField.setMinHeight("9rem");
        UiCss.TRAINING_ACTIVITY_DIALOG_INSTRUCTIONS.addTo(instructionField);
        instructionField.setValue(activity.getInstructions());
        instructionField.addValueChangeListener(_ -> invalidateDisplayedReviewConfirmation());
        if (activity.getStatus() == TrainingActivityLifecycleStatus.DRAFT) {
            showInstructionReview(trainingActivityService.getInstructionReviewSnapshot(activity.getId()));
        }

        safeBrowserField = new Checkbox("Safe Browser Mode");
        safeBrowserField.setHelperText("Disponible solo antes de lanzar la actividad.");
        safeBrowserField.setValue(activity.isSafeBrowserEnabled());
        safeBrowserField.setEnabled(activity.getStatus() == TrainingActivityLifecycleStatus.DRAFT);

        saveButton.addThemeVariants(ButtonVariant.PRIMARY);
        saveButton.setDisableOnClick(true);

        add(backdrop, panel);
        renderActivityMode();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        subscribeToAssignmentStateChanges(attachEvent.getUI());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        unsubscribeFromAssignmentStateChanges();
        super.onDetach(detachEvent);
    }

    private void subscribeToAssignmentStateChanges(UI ui) {
        unsubscribeFromAssignmentStateChanges();
        assignmentStateSubscription = assignmentStateBus.subscribe(notification -> {
            if (!notification.affectsTrainingActivity(original.getId())) {
                return;
            }
            ui.access(() -> {
                if (!isAttached()) {
                    return;
                }
                refreshActivitySnapshot();
                if (activityMode) {
                    renderActivityMode();
                }
                else {
                    refreshDisplayedReport();
                }
            });
        });
    }

    private void unsubscribeFromAssignmentStateChanges() {
        if (assignmentStateSubscription == null) {
            return;
        }
        try {
            assignmentStateSubscription.close();
        }
        catch (Exception exception) {
            // Subscription removal has no checked failure path.
        }
        assignmentStateSubscription = null;
    }

    private void renderActivityMode() {
        var activity = activitySnapshot;
        activityMode = true;
        displayedReportAssignmentId = null;
        panel.removeAll();
        panel.removeClassName("training-activity-overlay-panel--report");
        panel.addClassName("training-activity-overlay-panel--activity");
        var draft = activity.getStatus() == TrainingActivityLifecycleStatus.DRAFT;

        var title = new H3("Actividad: %s".formatted(activity.getTitle()));
        title.getStyle().set("margin", "0");

        titleField.setValue(activity.getTitle());
        titleField.setReadOnly(!draft);
        safeBrowserField.setValue(activity.isSafeBrowserEnabled());
        safeBrowserField.setEnabled(draft);

        Component instructionsComponent;
        if (draft) {
            instructionField.setValue(activity.getInstructions());
            showInstructionReview(trainingActivityService.getInstructionReviewSnapshot(activity.getId()));
            instructionsComponent = instructionField;
        }
        else {
            clearDisplayedReviewConfirmation();
            instructionField.resetReviewState();
            var readonlyInstructions = new Div();
            readonlyInstructions.setText(activity.getInstructions());
            readonlyInstructions.addClassName("training-activity-readonly-instructions");
            instructionsComponent = readonlyInstructions;
        }

        var body = new VerticalLayout(title, titleField, instructionsComponent, safeBrowserField, incidentSummary(), assignmentsGrid());
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidthFull();
        body.addClassName("training-activity-overlay-body");

        var closeActivityButton = new Button("Cerrar actividad", _ -> onCloseActivityClick());
        closeActivityButton.addThemeVariants(ButtonVariant.ERROR);
        closeActivityButton.setEnabled(activity.getStatus() == TrainingActivityLifecycleStatus.PUBLISHED);

        var closeButton = new Button("Cerrar", _ -> close());

        var footer = draft
                ? new HorizontalLayout(closeActivityButton, saveButton, closeButton)
                : new HorizontalLayout(closeActivityButton, closeButton);
        UiCss.TRAINING_ACTIVITY_OVERLAY_FOOTER.addTo(footer);
        footer.setPadding(false);
        footer.setSpacing(false);

        panel.add(body, footer);
    }

    private Grid<TrainingActivityAssignment> assignmentsGrid() {
        var grid = new Grid<>(TrainingActivityAssignment.class, false);

        grid.addColumn(this::studentName)
                .setHeader("Estudiante")
                .setWidth("0")
                .setFlexGrow(1)
                .setAutoWidth(false);

        grid.addColumn(this::assignmentStatusLabel)
                .setHeader("Estado")
                .setWidth("7rem")
                .setFlexGrow(0)
                .setAutoWidth(false);

        grid.addColumn(this::safeBrowserStatusLabel)
                .setHeader("Safe Browser")
                .setWidth("10rem")
                .setFlexGrow(0)
                .setAutoWidth(false);

        grid.addColumn(new ComponentRenderer<>(this::reportButton))
                .setHeader("Reporte")
                .setWidth("6.5rem")
                .setFlexGrow(0)
                .setAutoWidth(false);

        grid.addColumn(new ComponentRenderer<>(this::unlockButton))
                .setHeader("Incidente")
                .setWidth("8rem")
                .setFlexGrow(0)
                .setAutoWidth(false);

        grid.setEmptyStateText("No hay asignaciones todavía.");
        grid.setItems(trainingActivityService.listAssignments(original.getId()));
        grid.setWidthFull();

        grid.setHeight("min(26rem, 38vh)");

        return grid;
    }

    private String assignmentStatusLabel(TrainingActivityAssignment assignment) {
        return switch (assignment.getStatus()) {
            case ASSIGNED -> "Asignada";
            case STARTING -> "Preparando pregunta";
            case WAITING_FOR_ANSWER -> "Esperando respuesta";
            case WAITING_FOR_TUTOR -> "Analizando respuesta";
            case TEMPORARILY_UNAVAILABLE -> "Tutor no disponible temporalmente";
            case SUBMITTED -> "Enviada";
            case SKIPPED -> "Omitida";
            case EXPIRED -> "Vencida";
            case EXCUSED -> "Eximida";
        };
    }

    private String safeBrowserStatusLabel(TrainingActivityAssignment assignment) {
        if (!assignment.getTrainingActivity().isSafeBrowserEnabled()) {
            return "No requerido";
        }
        if (assignment.isSafeBrowserLocked()) {
            return "Bloqueada";
        }
        if (assignment.isSafeBrowserSessionActive()) {
            return "Activa";
        }
        return "Pendiente";
    }

    private void onSaveClick() {
        if (activitySnapshot.getStatus() != TrainingActivityLifecycleStatus.DRAFT) {
            Notification.show("Solo se pueden editar actividades en borrador");
            return;
        }
        var title = titleField.getValue().trim();
        var instruction = instructionField.getValue().trim();

        if (title.isBlank() || instruction.isBlank()) {
            Notification.show("El título y las instrucciones son obligatorios");
            return;
        }

        TrainingActivity updated;
        try {
            var currentSnapshot = trainingActivityService.getInstructionReviewSnapshot(original.getId());
            if (!matchesActivitySnapshot(title, instruction)
                    && !matchesDisplayedReviewConfirmation(title, instruction)) {
                showInstructionReview(trainingActivityService.reviewDraft(new TrainingActivitySaveCommand(
                        title,
                        instruction,
                        safeBrowserField.getValue(),
                        false)));
                return;
            }
            var confirmedReviewHash = confirmedReviewHashForSave(title, instruction, currentSnapshot);
            updated = trainingActivityService.update(original.getId(), new TrainingActivitySaveCommand(
                    title,
                    instruction,
                    safeBrowserField.getValue(),
                    confirmedReviewHash != null && !confirmedReviewHash.isBlank()));
        }
        catch (InstructionQualityReviewException exception) {
            if (exception.getReviewSnapshot() != null) {
                showInstructionReview(exception.getReviewSnapshot());
            }
            Notification.show(exception.getMessage());
            return;
        }
        catch (InstructionReviewUnavailableException exception) {
            if (exception.getReviewSnapshot() != null) {
                showInstructionReview(exception.getReviewSnapshot());
            }
            Notification.show(exception.getMessage());
            return;
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
            return;
        }
        finally {
            saveButton.setEnabled(true);
        }
        var snapshot = trainingActivityService.getInstructionReviewSnapshot(updated.getId());
        activitySnapshot = updated;
        showInstructionReview(snapshot);
        Notification.show(saveMessage());

        if (onSave != null) {
            onSave.accept(updated);
        }

        if (snapshot.qualityStatus() == InstructionQualityStatus.GOOD) {
            close();
        }
    }

    private boolean requiresVisibleReviewConfirmation(InstructionReviewSnapshotDto snapshot) {
        return snapshot != null && snapshot.requiresVisibleReviewConfirmation();
    }

    private String confirmedReviewHashForSave(
            String title,
            String instruction,
            InstructionReviewSnapshotDto currentSnapshot) {
        var confirmationSnapshot = confirmationSnapshotForSave(title, instruction, currentSnapshot);
        return requiresVisibleReviewConfirmation(confirmationSnapshot)
                ? confirmationSnapshot.reviewHash()
                : "";
    }

    private InstructionReviewSnapshotDto confirmationSnapshotForSave(
            String title,
            String instruction,
            InstructionReviewSnapshotDto currentSnapshot) {
        if (requiresVisibleReviewConfirmation(currentSnapshot) && matchesActivitySnapshot(title, instruction)) {
            return currentSnapshot;
        }
        if (matchesDisplayedReviewConfirmation(title, instruction)) {
            return displayedReviewSnapshot;
        }
        return null;
    }

    private boolean matchesActivitySnapshot(String title, String instruction) {
        return activitySnapshot != null
                && title.equals(activitySnapshot.getTitle().trim())
                && instruction.equals(activitySnapshot.getInstructions().trim());
    }

    private boolean matchesDisplayedReviewConfirmation(String title, String instruction) {
        return displayedReviewSnapshot != null
                && title.equals(displayedReviewTitle)
                && instruction.equals(displayedReviewInstructions);
    }

    private void invalidateDisplayedReviewConfirmation() {
        if (!matchesDisplayedReviewConfirmation(titleField.getValue().trim(), instructionField.getValue().trim())) {
            clearDisplayedReviewConfirmation();
        }
    }

    private void rememberDisplayedReviewConfirmation(InstructionReviewSnapshotDto reviewSnapshot) {
        if (reviewSnapshot == null) {
            clearDisplayedReviewConfirmation();
            return;
        }
        displayedReviewSnapshot = reviewSnapshot;
        displayedReviewTitle = titleField.getValue().trim();
        displayedReviewInstructions = instructionField.getValue().trim();
    }

    private void clearDisplayedReviewConfirmation() {
        displayedReviewSnapshot = null;
        displayedReviewTitle = "";
        displayedReviewInstructions = "";
    }

    private String saveMessage() {
        return "Actividad guardada";
    }

    private void onCloseActivityClick() {
        try {
            var updated = trainingActivityService.close(original.getId());
            activitySnapshot = updated;
            Notification.show("Actividad formativa cerrada");
            if (onSave != null) {
                onSave.accept(updated);
            }
            close();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    public void showInstructionReview(InstructionReviewSnapshotDto reviewSnapshot) {
        rememberDisplayedReviewConfirmation(reviewSnapshot);
        instructionField.setReviewSnapshot(reviewSnapshot);
    }

    private String studentName(TrainingActivityAssignment assignment) {
        var account = assignment.getGroupClassMember().getTenantAccount().getAccount();
        return "%s %s".formatted(account.getFirstName(), account.getLastName()).trim();
    }

    private Button reportButton(TrainingActivityAssignment assignment) {
        var button = new Button("Ver", _ -> renderReportMode(assignment.getId()));
        button.setEnabled(assignment.getStatus() == com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus.SUBMITTED);
        button.setWidthFull();
        return button;
    }

    private Button unlockButton(TrainingActivityAssignment assignment) {
        var button = new Button("Desbloquear", _ -> unlockAssignment(assignment));
        button.setEnabled(assignment.isSafeBrowserLocked());
        button.setWidthFull();
        return button;
    }

    private void unlockAssignment(TrainingActivityAssignment assignment) {
        try {
            safeBrowserModeService.unlockAssignment(assignment.getId());
            Notification.show("Asignación desbloqueada");
            renderActivityMode();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private Component incidentSummary() {
        var openAlerts = safeBrowserModeService.listOpenAlerts(original.getId());
        if (openAlerts.isEmpty()) {
            var empty = new Span("Sin alertas Safe Browser abiertas.");
            empty.addClassName("training-activity-safe-browser-empty");
            return empty;
        }
        var alert = openAlerts.getFirst();
        var text = new Span("Alerta Safe Browser: %d incidente(s), último evento %s"
                .formatted(alert.getIncidentCount(), alert.getLastEventAt()));
        text.addClassName("training-activity-safe-browser-alert");
        return text;
    }

    private void renderReportMode(java.util.UUID assignmentId) {
        try {
            renderReportMode(reportProjectionService.getForCurrentReviewer(assignmentId));
        }
        catch (RuntimeException exception) {
            renderActivityMode();
            Notification.show("No se pudo cargar el reporte de esta evaluación.");
        }
    }

    private void renderReportMode(TrainingActivityReportProjectionService.ReportProjection projection) {
        activityMode = false;
        displayedReportAssignmentId = projection.assignment().getId();
        panel.removeAll();
        panel.removeClassName("training-activity-overlay-panel--activity");
        panel.addClassName("training-activity-overlay-panel--report");
        panel.add(reportHeader(projection.assignment()), reportBody(projection), reportFooter());
    }

    private void refreshDisplayedReport() {
        if (displayedReportAssignmentId == null) {
            return;
        }
        try {
            renderReportMode(reportProjectionService.getForCurrentReviewer(displayedReportAssignmentId));
        }
        catch (RuntimeException exception) {
            Notification.show("No se pudo actualizar el reporte de esta evaluación.");
        }
    }

    private Component reportBody(TrainingActivityReportProjectionService.ReportProjection projection) {
        var content = new Div();
        content.addClassName("training-activity-report-content");
        switch (projection.status()) {
            case PENDING -> content.add(new Paragraph("El reporte está pendiente de generación. La transcripción ya está disponible."));
            case GENERATING -> content.add(new Paragraph("El reporte se está generando. La transcripción ya está disponible."));
            case FAILED -> {
                content.add(new Paragraph("El reporte no está disponible temporalmente. La transcripción permanece disponible."));
                var retry = new Button("Reintentar reporte", _ -> retryFailedReport(projection.assignment().getId()));
                retry.addThemeVariants(ButtonVariant.PRIMARY);
                content.add(retry);
            }
            case READY -> content.add(readyReport(projection));
        }
        content.add(transcriptSection(projection.turns().stream().filter(turn -> turn.answerText() != null).toList()));
        return content;
    }

    private void retryFailedReport(java.util.UUID assignmentId) {
        try {
            if (!reportProjectionService.retryFailedReport(assignmentId)) {
                Notification.show("El reporte ya no está disponible para reintento.");
                return;
            }
            renderReportMode(reportProjectionService.getForCurrentReviewer(assignmentId));
        }
        catch (RuntimeException exception) {
            Notification.show("No se pudo programar el reintento del reporte.");
        }
    }

    private Component readyReport(TrainingActivityReportProjectionService.ReportProjection projection) {
        var report = new Div();
        report.add(reportSection("Síntesis diagnóstica", List.of(projection.summary())));
        report.add(reportSection("Estado de evidencia", List.of(evidenceStatusLabel(projection.evidenceStatus()))));
        report.add(reportSection("Fortalezas observadas", projection.strengths().stream()
                .map(finding -> findingText(finding, projection)).toList()));
        report.add(reportSection("Aspectos a trabajar", projection.weaknesses().stream()
                .map(finding -> findingText(finding, projection)).toList()));
        report.add(reportSection("Evidencias observables", projection.observations().stream()
                .map(finding -> findingText(finding, projection)).toList()));
        report.add(reportSection("Recomendación docente", projection.recommendations()));
        return report;
    }

    private String findingText(
            com.wornux.data.entities.training_activity.TrainingActivityReportFinding finding,
            TrainingActivityReportProjectionService.ReportProjection projection) {
        var turnLabels = finding.evidenceReferences().stream()
                .map(reference -> projection.turns().stream()
                        .filter(turn -> turn.sequenceNumber() == reference.turnSequence())
                        .findFirst()
                        .map(turn -> "Turno %d".formatted(turn.sequenceNumber()))
                        .orElse("Turno no disponible"))
                .distinct()
                .toList();
        return "%s (%s)".formatted(finding.observation(), String.join(", ", turnLabels));
    }

    private Component reportSection(String heading, List<String> entries) {
        var section = new Div(new H4(heading));
        if (entries == null || entries.isEmpty()) {
            section.add(new Paragraph("No hay observaciones respaldadas para esta sección."));
            return section;
        }
        entries.forEach(entry -> section.add(new Paragraph(entry == null ? "" : entry)));
        return section;
    }

    private String evidenceStatusLabel(com.wornux.data.entities.training_activity.EvidenceStatus evidenceStatus) {
        if (evidenceStatus == null) {
            return "La evidencia disponible es limitada; las conclusiones son necesariamente provisionales.";
        }
        return switch (evidenceStatus) {
            case STRONG_EVIDENCE -> "La evidencia disponible permite conclusiones formativas con mayor confianza.";
            case PARTIAL_EVIDENCE -> "La evidencia disponible permite conclusiones parciales y debe interpretarse con prudencia.";
            case WEAK_EVIDENCE, NO_EVIDENCE -> "La evidencia disponible es limitada; las conclusiones son necesariamente provisionales.";
        };
    }

    private Component reportHeader(TrainingActivityAssignment assignment) {
        var title = new H3("Reporte de evaluación");
        title.getStyle().set("margin", "0");

        var student = new Paragraph(studentName(assignment));
        student.getStyle().set("margin", "0");

        var activity = new Span("Actividad: %s".formatted(assignment.getTrainingActivity().getTitle()));
        var status = createReportBadge(assignmentStatusLabel(assignment).toUpperCase(Locale.ROOT));

        var meta = new Div(activity, status);
        meta.addClassName("training-activity-report-meta");

        var submittedAt = assignment.getSubmittedAt();
        if (submittedAt != null) {
            var submitted = new Span(submittedAt.atZone(ZoneId.systemDefault()).format(REPORT_DATE_FORMATTER));
            meta.add(submitted);
        }

        var header = new Div(title, student, meta);
        header.addClassName("training-activity-report-header");

        return header;
    }

    private Component transcriptSection(List<TrainingActivityReportProjectionService.TurnProjection> turns) {
        var title = new H4("Transcripción");
        title.addClassName("training-activity-transcript-title");

        var section = new Div(title);
        section.addClassNames("training-activity-transcript-section", "training-activity-report-transcript");
        turns.stream()
                .map(this::questionConversationCard)
                .forEach(section::add);
        return section;
    }

    private Component questionConversationCard(TrainingActivityReportProjectionService.TurnProjection turn) {
        var title = new Span("Pregunta %d".formatted(turn.sequenceNumber()));
        title.addClassName("training-activity-conversation-title");

        var badge = new Span("RESPONDIDA");
        badge.addClassName("training-activity-conversation-badge");

        var header = new Div(title, badge);
        header.addClassName("training-activity-conversation-card-header");

        var body = new Div(
                conversationMessage("Tutor Socrático", turn.questionText(), true),
                conversationMessage("Estudiante", turn.answerText(), false));
        body.addClassName("training-activity-conversation-body");

        var card = new Div(header, body);
        card.addClassName("training-activity-conversation-card");
        return card;
    }

    private Component conversationMessage(String author, String text, boolean tutor) {
        var authorLabel = new Span(author);
        authorLabel.addClassName("training-activity-conversation-author");

        var messages = new MessagesList();
        messages.setWidthFull();
        messages.setItems(List.of(new MessageItem(
                text,
                java.time.Instant.EPOCH,
                author,
                tutor ? MessageItem.Variant.ASSISTANT : MessageItem.Variant.USER,
                false,
                false)));
        messages.addClassName("training-activity-conversation-messages");

        var row = new Div(authorLabel, messages);
        row.addClassName("training-activity-conversation-row");
        row.addClassName(tutor
                ? "training-activity-conversation-row--tutor"
                : "training-activity-conversation-row--student");
        return row;
    }

    private void refreshActivitySnapshot() {
        activitySnapshot = trainingActivityService.get(original.getId());
    }

    private Span createReportBadge(String text) {
        var badge = new Span(text);
        badge.addClassName("training-activity-report-badge");
        return badge;
    }

    private Component reportFooter() {
        var backButton = new Button("Volver a la actividad", _ -> renderActivityMode());

        var closeButton = new Button("Cerrar", _ -> close());
        closeButton.addThemeVariants(ButtonVariant.PRIMARY);

        var footer = new HorizontalLayout(backButton, closeButton);
        footer.addClassName("training-activity-report-footer");
        footer.setPadding(false);
        footer.setSpacing(false);
        
        return footer;
    }

    public void close() {
        unsubscribeFromAssignmentStateChanges();
        if (onClose != null) {
            onClose.run();
        }
    }

}
