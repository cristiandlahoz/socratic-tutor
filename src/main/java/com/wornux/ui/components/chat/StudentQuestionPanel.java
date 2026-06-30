package com.wornux.ui.components.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import com.wornux.dtos.chat.questions.*;
import com.wornux.ui.css.UiCss;
import org.jspecify.annotations.NonNull;

public class StudentQuestionPanel extends Composite<Div> {

    private final Span title = new Span();
    private final Span progress = new Span();
    private final Div questionViewport = new Div();
    private final Button previousButton = new Button(new Icon(VaadinIcon.ARROW_LEFT));
    private final Button nextButton = new Button(new Icon(VaadinIcon.ARROW_RIGHT));
    private final Button submitButton = new Button("Enviar");
    private final Div composerActions = new Div();
    private final Div responseComposer = new Div();
    private final Map<String, ValueSignal<Set<String>>> selectedOptionsByQuestion = new LinkedHashMap<>();
    private final Map<String, TextArea> customTextByQuestion = new LinkedHashMap<>();
    private final Map<String, Map<String, Button>> optionButtonsByQuestion = new LinkedHashMap<>();
    private final Map<String, Map<String, VerticalLayout>> optionRowsByQuestion = new LinkedHashMap<>();

    private StudentQuestionSet questionSet;
    private Consumer<StudentQuestionResponse> submitHandler = _ -> {};
    private boolean submitting;
    private int activeQuestionIndex;

    public StudentQuestionPanel() {
        var root = getContent();
        UiCss.CONVERSATION_QUESTION.addTo(root);

        UiCss.CONVERSATION_QUESTION_TITLE.addTo(title);
        UiCss.CONVERSATION_QUESTION_PROGRESS.addTo(progress);
        UiCss.CONVERSATION_QUESTION_VIEWPORT.addTo(questionViewport);

        previousButton.addThemeVariants(ButtonVariant.TERTIARY);
        UiCss.CONVERSATION_QUESTION_NAV_BUTTON.addTo(previousButton);
        previousButton.setAriaLabel("Pregunta anterior");
        previousButton.addClickListener(_ -> showPreviousQuestion());

        nextButton.addThemeVariants(ButtonVariant.TERTIARY);
        UiCss.CONVERSATION_QUESTION_NAV_BUTTON.addTo(nextButton);
        nextButton.setAriaLabel("Pregunta siguiente");
        nextButton.addClickListener(_ -> showNextQuestion());

        UiCss.CONVERSATION_QUESTION_SUBMIT_BUTTON.addTo(submitButton);
        submitButton.addThemeVariants(ButtonVariant.TERTIARY);
        submitButton.addClickListener(_ -> submitAnswers());

        UiCss.CONVERSATION_QUESTION_COMPOSER_ACTIONS.addTo(composerActions);
        composerActions.add(previousButton, nextButton, submitButton);

        UiCss.CONVERSATION_QUESTION_COMPOSER.addTo(responseComposer);

        var header = new Div(title, progress);
        UiCss.CONVERSATION_QUESTION_HEADER_ROW.addTo(header);

        root.add(header, questionViewport, responseComposer);
        updateSubmitEnabled();
    }

    public void setQuestionSet(StudentQuestionSet questionSet) {
        this.questionSet = questionSet;
        rebuild();
    }

    public void setSubmitHandler(Consumer<StudentQuestionResponse> submitHandler) {
        this.submitHandler = submitHandler == null ? _ -> {} : submitHandler;
    }

    public void setSubmitting(boolean submitting) {
        this.submitting = submitting;
        getContent().getElement().getClassList().set(UiCss.CONVERSATION_QUESTION_SUBMITTING.value(), submitting);
        updateSubmitEnabled();
    }

    private void rebuild() {
        selectedOptionsByQuestion.clear();
        customTextByQuestion.clear();
        optionButtonsByQuestion.clear();
        optionRowsByQuestion.clear();
        questionViewport.removeAll();
        responseComposer.removeAll();
        activeQuestionIndex = 0;

        var rootClasses = getContent().getElement().getClassList();
        if (questionSet == null) {
            title.setText("");
            progress.setText("");
            rootClasses.remove(UiCss.CONVERSATION_QUESTION_OPEN.value());
            updateSubmitEnabled();
            return;
        }

        rootClasses.add(UiCss.CONVERSATION_QUESTION_OPEN.value());

        for (int index = 0; index < questionSet.questions().size(); index++) {
            var questionKey = questionKey(index);
            selectedOptionsByQuestion.put(questionKey, new ValueSignal<>(Set.of()));
            optionButtonsByQuestion.put(questionKey, new LinkedHashMap<>());
            optionRowsByQuestion.put(questionKey, new LinkedHashMap<>());
            customTextByQuestion.put(questionKey, buildCustomTextArea(questionKey));
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
        var questionKey = questionKey(activeQuestionIndex);
        title.setText(activeQuestion.question());
        if (!activeQuestion.options().isEmpty()) {
            questionViewport.add(buildQuestionCard(activeQuestion, questionKey));
        }
        responseComposer.removeAll();
        configureCustomTextArea(customTextByQuestion.get(questionKey), activeQuestion.options().isEmpty());
        responseComposer.add(buildResponseComposer(questionKey));
        updateSubmitEnabled();
    }

    private Div buildQuestionCard(StudentQuestion question, String questionKey) {
        var card = new Div();
        UiCss.CONVERSATION_QUESTION_CARD.addTo(card);

        var options = new VerticalLayout();
        UiCss.CONVERSATION_QUESTION_OPTIONS.addTo(options);
        options.setPadding(false);
        options.setSpacing(false);
        options.setMargin(false);

        for (int optionIndex = 0; optionIndex < question.options().size(); optionIndex++) {
            options.add(buildOptionButton(questionKey, optionIndex, question.options().get(optionIndex)));
        }

        card.add(options);
        return card;
    }

    private Component buildOptionButton(String questionKey, int optionIndex, StudentQuestionOption option) {
        var optionKey = optionKey(optionIndex);
        var optionCopy = getOptionCopy(option);

        var infoButton = new Button(new Icon(VaadinIcon.INFO_CIRCLE_O));
        infoButton.addThemeVariants(ButtonVariant.TERTIARY);
        UiCss.CONVERSATION_QUESTION_OPTION_INFO.addTo(infoButton);
        infoButton.setAriaLabel("Ver detalle de %s".formatted(option.label()));

        var infoPopover = new Popover();
        infoPopover.setTarget(infoButton);
        infoPopover.setModal(false);
        UiCss.CONVERSATION_QUESTION_OPTION_POPOVER.addTo(infoPopover);
        var popoverDescription = new Paragraph(option.description());
        UiCss.CONVERSATION_QUESTION_OPTION_DESCRIPTION.addTo(popoverDescription);
        UiCss.CONVERSATION_QUESTION_OPTION_DESCRIPTION_POPOVER.addTo(popoverDescription);
        infoPopover.add(popoverDescription);

        var button = new Button(optionCopy);
        button.addThemeVariants(ButtonVariant.TERTIARY);
        UiCss.CONVERSATION_QUESTION_OPTION.addTo(button);
        button.getElement().setAttribute("data-question-id", questionKey);
        button.getElement().setAttribute("data-option-index", Integer.toString(optionIndex));
        button.getElement().setAttribute("aria-pressed", Boolean.FALSE.toString());
        button.addClickListener(_ -> toggleOption(questionKey, optionKey));

        var mobileHeader = new HorizontalLayout(button, infoButton);
        UiCss.CONVERSATION_QUESTION_OPTION_MOBILE_HEADER.addTo(mobileHeader);
        mobileHeader.setPadding(false);
        mobileHeader.setSpacing(false);
        mobileHeader.setMargin(false);
        mobileHeader.setDefaultVerticalComponentAlignment(Alignment.START);
        mobileHeader.setAlignItems(Alignment.START);

        var row = new VerticalLayout(mobileHeader, infoPopover);
        UiCss.CONVERSATION_QUESTION_OPTION_ROW.addTo(row);
        row.setPadding(false);
        row.setSpacing(false);
        row.setMargin(false);
        row.setWidthFull();

        optionButtonsByQuestion.get(questionKey).put(optionKey, button);
        optionRowsByQuestion.get(questionKey).put(optionKey, row);
        bindOptionSelection(questionKey, optionKey, button, row);
        return row;
    }

    private void bindOptionSelection(String questionId, String optionKey, Button button, VerticalLayout row) {
        var selectedSignal = selectedOptionsByQuestion.get(questionId);
        Signal.effect(row, () -> {
            var isSelected = selectedSignal.get().contains(optionKey);
            row.getElement().getClassList().set(UiCss.CONVERSATION_QUESTION_OPTION_SELECTED.value(), isSelected);
            applySelectedButtonStyle(button, isSelected);
        });
    }

    private void applySelectedButtonStyle(Button button, boolean isSelected) {
        button.getElement().getClassList().set(UiCss.CONVERSATION_QUESTION_OPTION_SELECTED.value(), isSelected);
        button.getElement().setAttribute("aria-pressed", Boolean.toString(isSelected));
        if (isSelected) {
            button.addThemeVariants(ButtonVariant.WARNING);
        }
        else {
            button.removeThemeVariants(ButtonVariant.WARNING);
        }
    }

    private static @NonNull VerticalLayout getOptionCopy(StudentQuestionOption option) {
        var label = new Span(option.label());
        UiCss.CONVERSATION_QUESTION_OPTION_LABEL.addTo(label);

        var inlineDescription = new Paragraph(option.description());
        UiCss.CONVERSATION_QUESTION_OPTION_DESCRIPTION.addTo(inlineDescription);
        UiCss.CONVERSATION_QUESTION_OPTION_DESCRIPTION_INLINE.addTo(inlineDescription);

        var optionCopy = new VerticalLayout(label, inlineDescription);
        UiCss.CONVERSATION_QUESTION_OPTION_COPY.addTo(optionCopy);
        optionCopy.setPadding(false);
        optionCopy.setSpacing(false);
        optionCopy.setMargin(false);
        return optionCopy;
    }

    private TextArea buildCustomTextArea(String questionId) {
        var customText = new TextArea();
        UiCss.CONVERSATION_QUESTION_CUSTOM_TEXT.addTo(customText);
        customText.setWidthFull();
        customText.getElement().setAttribute("data-question-id", questionId);
        customText.setValueChangeMode(ValueChangeMode.EAGER);
        customText.addValueChangeListener(_ -> updateSubmitEnabled());
        return customText;
    }

    private void configureCustomTextArea(TextArea customText, boolean openQuestion) {
        if (openQuestion) {
            customText.setPlaceholder("Escribe tu respuesta...");
            customText.setAriaLabel("Respuesta a la pregunta");
            return;
        }
        customText.setPlaceholder("Agrega contexto extra si quieres...");
        customText.setAriaLabel("Respuesta complementaria");
    }

    private Div buildResponseComposer(String questionKey) {
        var composer = new Div();
        UiCss.CONVERSATION_QUESTION_COMPOSER_WRAP.addTo(composer);
        composer.add(customTextByQuestion.get(questionKey));
        composer.add(composerActions);
        return composer;
    }

    private void toggleOption(String questionKey, String optionKey) {
        var selectedSignal = selectedOptionsByQuestion.get(questionKey);
        selectedSignal.update(current -> nextSelection(current, optionKey));
        updateSubmitEnabled();
    }

    private Set<String> nextSelection(Set<String> current, String optionKey) {
        var next = new LinkedHashSet<>(current);
        if (next.contains(optionKey)) {
            return Set.of();
        }
        return Set.of(optionKey);
    }

    private void submitAnswers() {
        if (questionSet == null || !canSubmit()) {
            return;
        }
        var answers = new ArrayList<StudentQuestionAnswer>();
        for (int index = 0; index < questionSet.questions().size(); index++) {
            final int currentIndex = index;
            var questionKey = questionKey(index);
            var selectedLabels = selectedOptions(questionKey).stream()
                    .map(optionKey -> selectedOptionLabel(questionSet.questions().get(currentIndex), optionKey))
                    .toList();
            var customText = customTextByQuestion.containsKey(questionKey)
                    ? customTextByQuestion.get(questionKey).getValue()
                    : "";
            answers.add(new StudentQuestionAnswer(questionKey, selectedLabels, customText));
        }
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
        nextButton
                .setEnabled(hasQuestionSet && !submitting && activeQuestionIndex < questionSet.questions().size() - 1);
        nextButton
                .setVisible(hasQuestionSet && !submitting && activeQuestionIndex < questionSet.questions().size() - 1);
    }

    private boolean canSubmit() {
        if (questionSet == null) {
            return false;
        }
        for (int index = 0; index < questionSet.questions().size(); index++) {
            var questionKey = questionKey(index);
            var selected = selectedOptions(questionKey);
            var customText = customTextByQuestion.containsKey(questionKey)
                    ? customTextByQuestion.get(questionKey).getValue().trim()
                    : "";
            if (selected.isEmpty() && customText.isBlank()) {
                return false;
            }
        }
        return true;
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

    private Set<String> selectedOptions(String questionId) {
        var selectedOptionsSignal = selectedOptionsByQuestion.get(questionId);
        return selectedOptionsSignal == null ? Set.of() : selectedOptionsSignal.peek();
    }

    private static String questionKey(int questionIndex) {
        return "q" + questionIndex;
    }

    private static String optionKey(int optionIndex) {
        return "o" + optionIndex;
    }

    private static String selectedOptionLabel(StudentQuestion question, String optionKey) {
        var optionIndex = Integer.parseInt(optionKey.substring(1));
        return question.options().get(optionIndex).label();
    }
}
