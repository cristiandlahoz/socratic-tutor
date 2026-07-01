package com.wornux.ui.training_activity;

import java.util.UUID;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.services.training_activity.TrainingAssignmentEvaluationService;
import com.wornux.ui.MainLayout;
import jakarta.annotation.security.PermitAll;

@Route(value = "training-activity/assignments", layout = MainLayout.class)
@PermitAll
public class TrainingAssignmentEvaluationView extends Composite<Div> implements HasUrlParameter<String> {

    private final TrainingAssignmentEvaluationService evaluationService;
    private final TextArea answerField = new TextArea("Your answer");
    private final Button submitButton = new Button("Submit answer");
    private final Paragraph question = new Paragraph();
    private UUID assignmentId;
    private TrainingActivityAssignment assignment;

    public TrainingAssignmentEvaluationView(TrainingAssignmentEvaluationService evaluationService) {
        this.evaluationService = evaluationService;

        answerField.setWidthFull();
        answerField.setMinHeight("10rem");
        answerField.setValueChangeMode(ValueChangeMode.EAGER);
        answerField.addValueChangeListener(event -> submitButton.setEnabled(!event.getValue().trim().isBlank()));

        submitButton.addThemeVariants(ButtonVariant.PRIMARY);
        submitButton.setEnabled(false);
        submitButton.addClickListener(_ -> submitAnswer());

        var content = getContent();
        content.addClassName("evaluation-view");
        content.add(new VerticalLayout(new H2("Training evaluation"), question, answerField, submitButton));
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        try {
            assignmentId = UUID.fromString(parameter);
            assignment = evaluationService.start(assignmentId);
            renderAssignment();
        }
        catch (IllegalArgumentException | SecurityException exception) {
            Notification.show(exception.getMessage());
            UI.getCurrent().navigate("student");
        }
    }

    private void renderAssignment() {
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            question.setText("Evaluation submitted. Your professor can now review the report.");
            answerField.setVisible(false);
            submitButton.setVisible(false);
            return;
        }
        question.setText(assignment.getCurrentQuestion());
        answerField.clear();
        answerField.setVisible(true);
        submitButton.setVisible(true);
        submitButton.setEnabled(false);
    }

    private void submitAnswer() {
        if (assignmentId == null || answerField.getValue().trim().isBlank()) {
            return;
        }
        assignment = evaluationService.answer(assignmentId, answerField.getValue());
        renderAssignment();
    }
}
