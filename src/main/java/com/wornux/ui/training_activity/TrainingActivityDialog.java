package com.wornux.ui.training_activity;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.ui.conversation.CodeMessageList;
import com.wornux.ui.conversation.CodeMessageListItem;
import com.wornux.ui.css.UiCss;

public class TrainingActivityDialog extends Div {

    private final transient TrainingActivity original;
    private final transient TrainingActivityService trainingActivityService;
    private final transient SafeBrowserModeService safeBrowserModeService;
    private final transient Consumer<TrainingActivity> onSave;
    private final transient Runnable onClose;

    private final Div panel = new Div();
    private final TextField titleField;
    private final TextArea instructionField;
    private final Checkbox safeBrowserField;

    public TrainingActivityDialog(
            TrainingActivity activity,
            TrainingActivityService trainingActivityService,
            SafeBrowserModeService safeBrowserModeService,
            Consumer<TrainingActivity> onSave,
            Runnable onClose) {
        this.original = activity;
        this.trainingActivityService = trainingActivityService;
        this.safeBrowserModeService = safeBrowserModeService;
        this.onSave = onSave;
        this.onClose = onClose;

        UiCss.TRAINING_ACTIVITY_OVERLAY.addTo(this);

        var backdrop = new Div();
        UiCss.TRAINING_ACTIVITY_OVERLAY_BACKDROP.addTo(backdrop);
        backdrop.addClickListener(_ -> close());

        UiCss.TRAINING_ACTIVITY_OVERLAY_PANEL.addTo(panel);

        titleField = new TextField("Título");
        titleField.setWidthFull();
        titleField.setValue(activity.getTitle());

        instructionField = new TextArea("Instrucciones");
        instructionField.setWidthFull();
        instructionField.setMinHeight("9rem");
        instructionField.setMaxHeight("14rem");
        instructionField.setValue(activity.getInstructions());

        safeBrowserField = new Checkbox("Safe Browser Mode");
        safeBrowserField.setHelperText("Disponible solo antes de lanzar la actividad.");
        safeBrowserField.setValue(activity.isSafeBrowserEnabled());
        safeBrowserField.setEnabled(activity.getStatus() == TrainingActivityLifecycleStatus.DRAFT);

        add(backdrop, panel);
        renderActivityMode();
    }

    private void renderActivityMode() {
        panel.removeAll();
        panel.removeClassName("training-activity-overlay-panel--report");
        panel.addClassName("training-activity-overlay-panel--activity");

        var title = new H3("Actividad: %s".formatted(original.getTitle()));
        title.getStyle().set("margin", "0");

        titleField.setValue(original.getTitle());
        instructionField.setValue(original.getInstructions());
        safeBrowserField.setValue(original.isSafeBrowserEnabled());

        var body = new VerticalLayout(title, titleField, instructionField, safeBrowserField, incidentSummary(), assignmentsGrid());
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidthFull();
        body.addClassName("training-activity-overlay-body");

        var saveButton = new Button("Guardar cambios", _ -> onSaveClick());
        saveButton.addThemeVariants(ButtonVariant.PRIMARY);

        var closeActivityButton = new Button("Cerrar actividad", _ -> onCloseActivityClick());
        closeActivityButton.addThemeVariants(ButtonVariant.ERROR);
        closeActivityButton.setEnabled(original.getStatus() == TrainingActivityLifecycleStatus.PUBLISHED);

        var closeButton = new Button("Cerrar", _ -> close());

        var footer = new HorizontalLayout(closeActivityButton, saveButton, closeButton);
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

        // Más alto para clases reales con más estudiantes.
        grid.setHeight("min(34rem, 50vh)");

        return grid;
    }

    private String assignmentStatusLabel(TrainingActivityAssignment assignment) {
        return switch (assignment.getStatus()) {
            case ASSIGNED -> "Asignada";
            case STARTED -> "Iniciada";
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
        var title = titleField.getValue().trim();
        var instruction = instructionField.getValue().trim();

        if (title.isBlank() || instruction.isBlank()) {
            Notification.show("El título y las instrucciones son obligatorios");
            return;
        }

        var updated = trainingActivityService.update(original.getId(), title, instruction, safeBrowserField.getValue());
        Notification.show("Actividad formativa actualizada");

        if (onSave != null) {
            onSave.accept(updated);
        }

        close();
    }

    private void onCloseActivityClick() {
        try {
            var updated = trainingActivityService.close(original.getId());
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

    private String studentName(TrainingActivityAssignment assignment) {
        var account = assignment.getGroupClassMember().getTenantAccount().getAccount();
        return "%s %s".formatted(account.getFirstName(), account.getLastName()).trim();
    }

    private Button reportButton(TrainingActivityAssignment assignment) {
        var button = new Button("Ver", _ -> renderReportMode(assignment));
        button.setEnabled(assignment.getFinalReport() != null && !assignment.getFinalReport().isBlank());
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

    private void renderReportMode(TrainingActivityAssignment assignment) {
        panel.removeAll();
        panel.removeClassName("training-activity-overlay-panel--activity");
        panel.addClassName("training-activity-overlay-panel--report");

        panel.add(reportHeader(assignment), reportBody(assignment), reportFooter());
    }

    private Component reportHeader(TrainingActivityAssignment assignment) {
        var title = new H3("Reporte de evaluación");
        title.getStyle().set("margin", "0");

        var student = new Paragraph(studentName(assignment));
        student.getStyle().set("margin", "0");

        var activity = new Paragraph(assignment.getTrainingActivity().getTitle());
        activity.getStyle()
                .set("margin", "0")
                .set("opacity", "0.72");

        var header = new Div(title, student, activity);
        header.addClassName("training-activity-report-header");

        return header;
    }

    private Component reportBody(TrainingActivityAssignment assignment) {
        var reportList = new CodeMessageList();
        reportList.setMarkdown(true);
        reportList.setWidthFull();

        var createdAt = assignment.getSubmittedAt() != null
                ? assignment.getSubmittedAt()
                : Instant.now();

        var reportItem = new CodeMessageListItem(
                assignment.getFinalReport(),
                createdAt,
                "Tutor Socrático");

        reportItem.addClass(UiCss.CONVERSATION_MESSAGE_ASSISTANT);
        reportList.setItems(List.of(reportItem));

        var reportContent = new Div(reportList);
        reportContent.addClassName("training-activity-report-content");

        return reportContent;
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
        if (onClose != null) {
            onClose.run();
        }
    }
}
