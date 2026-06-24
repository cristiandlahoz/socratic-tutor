package com.wornux.ui.training_activity;

import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.services.training_activity.TrainingActivityService;

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

        addClassName("evaluation-overlay");

        var backdrop = new Div();
        backdrop.addClassName("evaluation-overlay-backdrop");
        backdrop.addClickListener(_ -> close());

        var panel = new Div();
        panel.addClassName("evaluation-overlay-panel");

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

        var blocker = new Div();
        blocker.setText(
            "Formative activity definitions are available, but assignment execution and report persistence require an extended data model.");
        blocker.getStyle()
                .set("padding", "1rem")
                .set("border-radius", "0.75rem")
                .set("background", "rgba(255,255,255,0.04)");

        var body = new VerticalLayout(title, titleField, instructionField, blocker);
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidthFull();

        var saveButton = new Button("Save changes", _ -> onSaveClick());
        saveButton.addThemeVariants(ButtonVariant.PRIMARY);

        var closeButton = new Button("Close", _ -> close());

        var footer = new HorizontalLayout(saveButton, closeButton);
        footer.addClassName("evaluation-overlay-footer");
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
}
