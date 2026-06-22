package com.wornux.ui.evaluation;

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
import com.wornux.data.entities.evaluation.Evaluation;
import com.wornux.services.evaluation.EvaluationService;

public class EvaluationDialog extends Div {

    private final Evaluation original;
    private final EvaluationService evaluationService;
    private final Consumer<Evaluation> onSave;
    private final Runnable onClose;
    private final TextField titleField;
    private final TextArea instructionField;

    public EvaluationDialog(Evaluation evaluation, EvaluationService evaluationService, Consumer<Evaluation> onSave, Runnable onClose) {
        this.original = evaluation;
        this.evaluationService = evaluationService;
        this.onSave = onSave;
        this.onClose = onClose;

        addClassName("evaluation-overlay");

        var backdrop = new Div();
        backdrop.addClassName("evaluation-overlay-backdrop");
        backdrop.addClickListener(_ -> close());

        var panel = new Div();
        panel.addClassName("evaluation-overlay-panel");

        var title = new H3("Evaluation: " + evaluation.getTitle());
        title.getStyle().set("margin", "0");

        titleField = new TextField("Title");
        titleField.setWidthFull();
        titleField.setValue(evaluation.getTitle());

        instructionField = new TextArea("Instructions");
        instructionField.setWidthFull();
        instructionField.setMinHeight("9rem");
        instructionField.setMaxHeight("14rem");
        instructionField.setValue(evaluation.getInstructions());

        var blocker = new Div();
        blocker.setText("UC-002 safe scope: evaluation definitions are active on the target ERD, but assignment execution and report payload persistence remain blocked until the target model is extended.");
        blocker.getStyle().set("padding", "1rem").set("border-radius", "0.75rem").set("background", "rgba(255,255,255,0.04)");

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
        var updated = evaluationService.update(original.getId(), title, instruction);
        Notification.show("Evaluation updated");
        if (onSave != null) {
            onSave.accept(updated);
        }
        close();
    }
}
