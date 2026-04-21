package com.wornux.chat.ui;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.wornux.chat.questions.StudentQuestion;
import com.wornux.chat.questions.StudentQuestionAnswer;
import com.wornux.chat.questions.StudentQuestionOption;
import com.wornux.chat.questions.StudentQuestionResponse;
import com.wornux.chat.questions.StudentQuestionSet;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class StudentQuestionPanel extends Composite<Div> {

    private final Span title = new Span();
    private final Div questionList = new Div();
    private final Button submitButton = new Button("Continuar");
    private final Map<String, Set<String>> selectedOptionsByQuestion = new LinkedHashMap<>();
    private final Map<String, TextArea> customTextByQuestion = new LinkedHashMap<>();
    private final Map<String, Map<String, Button>> optionButtonsByQuestion = new LinkedHashMap<>();

    private StudentQuestionSet questionSet;
    private Consumer<StudentQuestionResponse> submitHandler = _ -> {
    };
    private boolean submitting;

    public StudentQuestionPanel() {
        var root = getContent();
        root.addClassName("chat-question-panel");

        title.addClassName("chat-question-panel-title");
        questionList.addClassName("chat-question-list");

        submitButton.addClassName("chat-question-submit");
        submitButton.addThemeVariants(ButtonVariant.TERTIARY);
        submitButton.addClickListener(_ -> submitAnswers());

        var actions = new Div(submitButton);
        actions.addClassName("chat-question-actions");

        root.add(title, questionList, actions);
        updateSubmitEnabled();
    }

    public void setQuestionSet(StudentQuestionSet questionSet) {
        this.questionSet = questionSet;
        rebuild();
    }

    public void setSubmitHandler(Consumer<StudentQuestionResponse> submitHandler) {
        this.submitHandler = submitHandler == null ? _ -> {
        } : submitHandler;
    }

    public void setSubmitting(boolean submitting) {
        this.submitting = submitting;
        getContent().getElement().getClassList().set("is-submitting", submitting);
        updateSubmitEnabled();
    }

    private void rebuild() {
        selectedOptionsByQuestion.clear();
        customTextByQuestion.clear();
        optionButtonsByQuestion.clear();
        questionList.removeAll();

        var rootClasses = getContent().getElement().getClassList();
        if (questionSet == null) {
            title.setText("");
            rootClasses.remove("is-open");
            updateSubmitEnabled();
            return;
        }

        title.setText(questionSet.title());
        rootClasses.add("is-open");

        for (StudentQuestion question : questionSet.questions()) {
            selectedOptionsByQuestion.put(question.id(), new LinkedHashSet<>());
            optionButtonsByQuestion.put(question.id(), new LinkedHashMap<>());
            questionList.add(buildQuestionCard(question));
        }

        updateSubmitEnabled();
    }

    private Div buildQuestionCard(StudentQuestion question) {
        var card = new Div();
        card.addClassName("chat-question-card");

        var header = new Span(question.header());
        header.addClassName("chat-question-header");

        var prompt = new Paragraph(question.question());
        prompt.addClassName("chat-question-prompt");

        var options = new VerticalLayout();
        options.addClassName("chat-question-options");
        options.setPadding(false);
        options.setSpacing(false);
        options.setMargin(false);

        for (StudentQuestionOption option : question.options()) {
            options.add(buildOptionButton(question, option));
        }

        card.add(header, prompt, options);
        if (question.allowCustomText()) {
            var customText = buildCustomTextArea(question);
            customTextByQuestion.put(question.id(), customText);
            card.add(customText);
        }
        return card;
    }

    private Button buildOptionButton(StudentQuestion question, StudentQuestionOption option) {
        var label = new Span(option.label());
        label.addClassName("chat-question-option-label");

        var description = new Span(option.description());
        description.addClassName("chat-question-option-description");

        var text = new Div(label, description);
        text.addClassName("chat-question-option-copy");

        var button = new Button(text);
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.addClassName("chat-question-option");
        button.addClickListener(_ -> toggleOption(question, option.label()));

        optionButtonsByQuestion.get(question.id()).put(option.label(), button);
        return button;
    }

    private TextArea buildCustomTextArea(StudentQuestion question) {
        var customText = new TextArea();
        customText.addClassName("chat-question-custom-text");
        customText.setWidthFull();
        customText.setPlaceholder("Agrega contexto extra si quieres...");
        customText.setAriaLabel("Respuesta complementaria");
        customText.setValueChangeMode(ValueChangeMode.EAGER);
        customText.addValueChangeListener(_ -> updateSubmitEnabled());
        return customText;
    }

    private void toggleOption(StudentQuestion question, String optionLabel) {
        var selected = selectedOptionsByQuestion.get(question.id());
        if (question.multiSelect()) {
            if (!selected.add(optionLabel)) {
                selected.remove(optionLabel);
            }
        } else {
            if (selected.contains(optionLabel)) {
                selected.clear();
            } else {
                selected.clear();
                selected.add(optionLabel);
            }
        }
        refreshOptionSelection(question.id());
        updateSubmitEnabled();
    }

    private void refreshOptionSelection(String questionId) {
        var selected = selectedOptionsByQuestion.get(questionId);
        optionButtonsByQuestion.getOrDefault(questionId, Map.of()).forEach((label, button) ->
                button.getElement().getClassList().set("is-selected", selected.contains(label)));
    }

    private void submitAnswers() {
        if (questionSet == null || !canSubmit()) {
            return;
        }
        var answers = questionSet.questions().stream()
                .map(question -> new StudentQuestionAnswer(
                        question.id(),
                        selectedOptionsByQuestion.getOrDefault(question.id(), Set.of()).stream().toList(),
                        customTextByQuestion.containsKey(question.id()) ? customTextByQuestion.get(question.id()).getValue() : ""))
                .toList();
        submitHandler.accept(new StudentQuestionResponse(answers));
    }

    private void updateSubmitEnabled() {
        submitButton.setEnabled(!submitting && canSubmit());
    }

    private boolean canSubmit() {
        if (questionSet == null) {
            return false;
        }
        return questionSet.questions().stream().allMatch(question -> {
            var selected = selectedOptionsByQuestion.getOrDefault(question.id(), Set.of());
            var customText = customTextByQuestion.containsKey(question.id()) ? customTextByQuestion.get(question.id()).getValue().trim() : "";
            return !selected.isEmpty() || !customText.isBlank();
        });
    }
}
