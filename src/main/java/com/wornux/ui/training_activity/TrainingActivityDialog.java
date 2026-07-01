package com.wornux.ui.training_activity;

import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.ui.css.UiCss;

public class TrainingActivityDialog extends Div {

    private final TrainingActivity original;
    private final TrainingActivityService trainingActivityService;
    private final Consumer<TrainingActivity> onSave;
    private final Runnable onClose;
    private final TextField titleField;
    private final TextArea instructionField;

    public TrainingActivityDialog(
            TrainingActivity activity,
            TrainingActivityService trainingActivityService,
            Consumer<TrainingActivity> onSave,
            Runnable onClose) {
        this.original = activity;
        this.trainingActivityService = trainingActivityService;
        this.onSave = onSave;
        this.onClose = onClose;

        UiCss.TRAINING_ACTIVITY_OVERLAY.addTo(this);

        var backdrop = new Div();
        UiCss.TRAINING_ACTIVITY_OVERLAY_BACKDROP.addTo(backdrop);
        backdrop.addClickListener(_ -> close());

        var panel = new Div();
        UiCss.TRAINING_ACTIVITY_OVERLAY_PANEL.addTo(panel);

        var title = new H3("Activity: %s".formatted(activity.getTitle()));
        title.getStyle().set("margin", "0");

        titleField = new TextField("Title");
        titleField.setWidthFull();
        titleField.setValue(activity.getTitle());

        instructionField = new TextArea("Instructions");
        instructionField.setWidthFull();
        instructionField.setMinHeight("9rem");
        instructionField.setMaxHeight("14rem");
        instructionField.setValue(activity.getInstructions());

        var assignmentsGrid = new Grid<>(TrainingActivityAssignment.class, false);
        assignmentsGrid.addColumn(this::studentName).setHeader("Student").setAutoWidth(true).setFlexGrow(1);
        assignmentsGrid.addColumn(assignment -> assignment.getStatus().name()).setHeader("Status").setAutoWidth(true);
        assignmentsGrid.addColumn(new ComponentRenderer<>(this::reportButton)).setHeader("Report").setAutoWidth(true);
        assignmentsGrid.setEmptyStateText("No student assignments yet.");
        assignmentsGrid.setItems(trainingActivityService.listAssignments(activity.getId()));
        assignmentsGrid.setWidthFull();

        var body = new VerticalLayout(title, titleField, instructionField, assignmentsGrid);
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidthFull();

        var saveButton = new Button("Save changes", _ -> onSaveClick());
        saveButton.addThemeVariants(ButtonVariant.PRIMARY);

        var closeButton = new Button("Close", _ -> close());

        var footer = new HorizontalLayout(saveButton, closeButton);
        UiCss.TRAINING_ACTIVITY_OVERLAY_FOOTER.addTo(footer);
        footer.setPadding(false);
        footer.setSpacing(true);

        panel.add(body, footer);
        add(backdrop, panel);
    }

    public void close() {
        if (onClose != null) {
            onClose.run();
        }
    }

    private void onSaveClick() {
        var title = titleField.getValue().trim();
        var instruction = instructionField.getValue().trim();
        if (title.isBlank() || instruction.isBlank()) {
            Notification.show("Title and instructions are required");
            return;
        }
        var updated = trainingActivityService.update(original.getId(), title, instruction);
        Notification.show("Formative activity updated");
        if (onSave != null) {
            onSave.accept(updated);
        }
        close();
    }

    private String studentName(TrainingActivityAssignment assignment) {
        var account = assignment.getGroupClassMember().getTenantAccount().getAccount();
        return "%s %s".formatted(account.getFirstName(), account.getLastName()).trim();
    }

    private Button reportButton(TrainingActivityAssignment assignment) {
        var button = new Button("View report", _ -> openReport(assignment));
        button.setEnabled(assignment.getFinalReport() != null && !assignment.getFinalReport().isBlank());
        return button;
    }

    private void openReport(TrainingActivityAssignment assignment) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Evaluation report");
        var report = new Pre(assignment.getFinalReport());
        report.addClassName("evaluation-report-markdown");
        report.getStyle().set("white-space", "pre-wrap");
        dialog.add(new Paragraph(studentName(assignment)), report);
        dialog.getFooter().add(new Button("Close", _ -> dialog.close()));
        dialog.open();
    }
}
