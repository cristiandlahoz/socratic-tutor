package com.wornux.chat.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.wornux.chat.questions.StudentQuestion;
import com.wornux.chat.questions.StudentQuestionAnswer;
import com.wornux.chat.questions.StudentQuestionOption;
import com.wornux.chat.questions.StudentQuestionResponse;
import com.wornux.chat.questions.StudentQuestionSet;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class StudentQuestionPanel extends Composite<Div> {

    private final Span title = new Span();
    private final Span progress = new Span();
    private final Div questionViewport = new Div();
    private final Button previousButton = new Button(new Icon(VaadinIcon.ARROW_LEFT));
    private final Button nextButton = new Button(new Icon(VaadinIcon.ARROW_RIGHT));
    private final Button submitButton = new Button("Enviar");
    private final Div composerActions = new Div();
    private final Div responseComposer = new Div();
    private final Map<String, Set<String>> selectedOptionsByQuestion = new LinkedHashMap<>();
    private final Map<String, TextArea> customTextByQuestion = new LinkedHashMap<>();
    private final Map<String, Map<String, VerticalLayout>> optionRowsByQuestion = new LinkedHashMap<>();

    private StudentQuestionSet questionSet;
    private Consumer<StudentQuestionResponse> submitHandler = _ -> {
    };
    private boolean submitting;
    private int activeQuestionIndex;

    public StudentQuestionPanel() {
        var root = getContent();
        root.addClassName("chat-question-panel");

        title.addClassName("chat-question-panel-title");
        progress.addClassName("chat-question-progress");
        questionViewport.addClassName("chat-question-viewport");

        previousButton.addThemeVariants(ButtonVariant.TERTIARY);
        previousButton.addClassName("chat-question-nav-button");
        previousButton.setAriaLabel("Pregunta anterior");
        previousButton.addClickListener(_ -> showPreviousQuestion());

        nextButton.addThemeVariants(ButtonVariant.TERTIARY);
        nextButton.addClassName("chat-question-nav-button");
        nextButton.setAriaLabel("Pregunta siguiente");
        nextButton.addClickListener(_ -> showNextQuestion());

        submitButton.addClassName("chat-question-submit");
        submitButton.addThemeVariants(ButtonVariant.TERTIARY);
        submitButton.addClickListener(_ -> submitAnswers());

        composerActions.addClassName("chat-question-composer-actions");
        composerActions.add(previousButton, nextButton, submitButton);

        responseComposer.addClassName("chat-question-composer");

        var header = new Div(title, progress);
        header.addClassName("chat-question-header-row");

        root.add(header, questionViewport, responseComposer);
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
        optionRowsByQuestion.clear();
        questionViewport.removeAll();
        responseComposer.removeAll();
        activeQuestionIndex = 0;

        var rootClasses = getContent().getElement().getClassList();
        if (questionSet == null) {
            title.setText("");
            progress.setText("");
            rootClasses.remove("is-open");
            updateSubmitEnabled();
            return;
        }

        title.setText(questionSet.title());
        rootClasses.add("is-open");

        for (StudentQuestion question : questionSet.questions()) {
            selectedOptionsByQuestion.put(question.id(), new LinkedHashSet<>());
            optionRowsByQuestion.put(question.id(), new LinkedHashMap<>());
            customTextByQuestion.put(question.id(), buildCustomTextArea());
        }

        renderActiveQuestion();
        updateSubmitEnabled();
    }

    private void renderActiveQuestion() {
        questionViewport.removeAll();
        if (questionSet == null || questionSet.questions().isEmpty()) {
            progress.setText("");
            return;
        }

        var totalQuestions = questionSet.questions().size();
        activeQuestionIndex = Math.clamp(activeQuestionIndex, 0, totalQuestions - 1);
        progress.setText("%d / %d".formatted(activeQuestionIndex + 1, totalQuestions));
        var activeQuestion = questionSet.questions().get(activeQuestionIndex);
        questionViewport.add(buildQuestionCard(activeQuestion));
        responseComposer.removeAll();
        responseComposer.add(buildResponseComposer(activeQuestion));
        updateSubmitEnabled();
    }

    private Div buildQuestionCard(StudentQuestion question) {
        var card = new Div();
        card.addClassName("chat-question-card");

        var header = new Span(question.header());
        header.addClassName("chat-question-header");

        var prompt = new Span(question.question());
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
        return card;
    }

    private Component buildOptionButton(StudentQuestion question, StudentQuestionOption option) {
        var optionCopy = getOptionCopy(option);

        var infoButton = new Button(new Icon(VaadinIcon.INFO_CIRCLE_O));
        infoButton.addThemeVariants(ButtonVariant.TERTIARY);
        infoButton.addClassName("chat-question-option-info");
        infoButton.setAriaLabel("Ver detalle de " + option.label());

        var infoPopover = new Popover();
        infoPopover.setTarget(infoButton);
        infoPopover.setModal(false);
        infoPopover.addClassName("chat-question-option-popover");
        var popoverDescription = new Paragraph(option.description());
        popoverDescription.addClassNames(
                "chat-question-option-description",
                "chat-question-option-description-popover");
        infoPopover.add(popoverDescription);

        var button = new Button(optionCopy);
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.addClassName("chat-question-option");
        button.addClickListener(_ -> toggleOption(question, option.label()));

        var mobileHeader = new HorizontalLayout(button, infoButton);
        mobileHeader.addClassName("chat-question-option-mobile-header");
        mobileHeader.setPadding(false);
        mobileHeader.setSpacing(false);
        mobileHeader.setMargin(false);
        mobileHeader.setDefaultVerticalComponentAlignment(Alignment.START);
        mobileHeader.setAlignItems(Alignment.START);

        var row = new VerticalLayout(mobileHeader, infoPopover);
        row.addClassName("chat-question-option-row");
        row.setPadding(false);
        row.setSpacing(false);
        row.setMargin(false);
        row.setWidthFull();
        row.getElement().getClassList().set(
                "is-selected",
                selectedOptionsByQuestion.getOrDefault(question.id(), Set.of()).contains(option.label()));

        optionRowsByQuestion.get(question.id()).put(option.label(), row);
        return row;
    }

    private static @NonNull VerticalLayout getOptionCopy(StudentQuestionOption option) {
        var label = new Span(option.label());
        label.addClassName("chat-question-option-label");

        var inlineDescription = new Paragraph(option.description());
        inlineDescription.addClassNames(
                "chat-question-option-description",
                "chat-question-option-description-inline");

        var optionCopy = new VerticalLayout(label, inlineDescription);
        optionCopy.addClassName("chat-question-option-copy");
        optionCopy.setPadding(false);
        optionCopy.setSpacing(false);
        optionCopy.setMargin(false);
        return optionCopy;
    }

    private TextArea buildCustomTextArea() {
        var customText = new TextArea();
        customText.addClassName("chat-question-custom-text");
        customText.setWidthFull();
        customText.setPlaceholder("Agrega contexto extra si quieres...");
        customText.setAriaLabel("Respuesta complementaria");
        customText.setValueChangeMode(ValueChangeMode.EAGER);
        customText.addValueChangeListener(_ -> updateSubmitEnabled());
        return customText;
    }

    private Div buildResponseComposer(StudentQuestion question) {
        var composer = new Div();
        composer.addClassName("chat-question-composer-wrap");
        composer.add(customTextByQuestion.get(question.id()));
        composer.add(composerActions);
        return composer;
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
        optionRowsByQuestion.getOrDefault(questionId, Map.of()).forEach((label, row) ->
                row.getElement().getClassList().set("is-selected", selected.contains(label)));
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
        var hasQuestionSet = questionSet != null;
        submitButton.setEnabled(!submitting && canSubmit());
        submitButton.setVisible(hasQuestionSet && activeQuestionIndex == questionSet.questions().size() - 1);
        updateNavigationState();
    }

    private void updateNavigationState() {
        var hasQuestionSet = questionSet != null && !questionSet.questions().isEmpty();
        previousButton.setEnabled(hasQuestionSet && !submitting && activeQuestionIndex > 0);
        previousButton.setVisible(hasQuestionSet && !submitting && activeQuestionIndex > 0);
        nextButton.setEnabled(hasQuestionSet && !submitting && activeQuestionIndex < questionSet.questions().size() - 1);
        nextButton.setVisible(hasQuestionSet && !submitting && activeQuestionIndex < questionSet.questions().size() - 1);
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

    private void showPreviousQuestion() {
        if (questionSet == null || activeQuestionIndex == 0) {
            return;
        }
        activeQuestionIndex--;
        renderActiveQuestion();
    }

    private void showNextQuestion() {
        if (questionSet == null || activeQuestionIndex >= questionSet.questions().size() - 1) {
            return;
        }
        activeQuestionIndex++;
        renderActiveQuestion();
    }
}
