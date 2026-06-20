package com.wornux.ui.evaluation;

import java.util.UUID;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.wornux.services.evaluation.EvaluationChatService;
import com.wornux.services.evaluation.EvaluationRunService;
import com.wornux.ui.chat.BrailleSpinner;

@Route(value = "evaluations/run", layout = com.wornux.ui.MainLayout.class)
public class EvaluationChatView extends Composite<Div> implements HasUrlParameter<String> {

    private final EvaluationRunService runService;
    private final EvaluationChatService chatService;

    private UUID runId;
    private UUID evaluationId;
    private final Div titleLabel = new Div();
    private final Span progressLabel = new Span();
    private final Div messagesContainer = new Div();
    private final TextArea answerField = new TextArea();
    private final Button sendButton = new Button(new Icon(VaadinIcon.ARROW_UP));
    private final Button backButton = new Button("Volver a actividades formativas", new Icon(VaadinIcon.ARROW_LEFT));
    private final Div scrollRegion = new Div();
    private final BrailleSpinner loadingSpinner = new BrailleSpinner();
    private final Span loadingLabel = new Span();
    private final Div loadingIndicator = new Div(loadingSpinner, loadingLabel);
    private boolean complete = false;
    private static final String NEXT_QUESTION_LOADING_TEXT = "Formulando la siguiente pregunta...";
    private static final String REPORT_LOADING_TEXT = "Generando reporte formativo...";

    public EvaluationChatView(EvaluationRunService runService, EvaluationChatService chatService) {
        this.runService = runService;
        this.chatService = chatService;

        var content = getContent();
        content.setSizeFull();
        content.addClassName("chat-view");

        var header = buildHeader();

        messagesContainer.addClassName("chat-thread");
        scrollRegion.setSizeFull();
        scrollRegion.addClassName("chat-scroll-region");
        scrollRegion.add(messagesContainer);

        loadingIndicator.addClassName("evaluation-loading-indicator");
        loadingSpinner.addClassName("evaluation-loading-spinner");
        loadingLabel.addClassName("evaluation-loading-label");
        loadingLabel.setText(NEXT_QUESTION_LOADING_TEXT);
        loadingIndicator.setVisible(false);

        answerField.setWidthFull();
        answerField.setPlaceholder("Escribí tu respuesta...");
        answerField.addClassName("chat-composer-input");
        answerField.setAriaLabel("Escribí tu respuesta");
        answerField.setValueChangeMode(ValueChangeMode.EAGER);

        sendButton.addClassName("chat-composer-send");
        sendButton.setAriaLabel("Enviar respuesta");
        sendButton.addClickListener(_ -> onSend());

        // keydown: prevent Enter from inserting newline (value not yet committed)
        answerField.getElement()
                .addEventListener("keydown", _ -> {})
                .setFilter("event.key === 'Enter' && !event.shiftKey")
                .preventDefault();
        // keyup: submit on Enter (value IS committed by keyup; EAGER mode keeps it synced)
        answerField.getElement()
                .addEventListener("keyup", _ -> onSend())
                .setFilter("event.key === 'Enter' && !event.shiftKey");

        var composer = new Div(answerField, sendButton);
        composer.addClassName("chat-composer-wrap");

        var inputArea = new Div(loadingIndicator, composer);
        inputArea.addClassName("chat-composer-shell");

        backButton.addThemeVariants(ButtonVariant.TERTIARY);
        backButton.setVisible(false);
        backButton.addClickListener(_ -> getUI().ifPresent(ui -> ui.navigate(EvaluationView.class)));

        content.add(header, scrollRegion, inputArea, backButton);
    }

    private Div buildHeader() {
        titleLabel.addClassName("evaluation-run-title");
        titleLabel.setText("Actividad formativa en curso");

        progressLabel.addClassName("evaluation-run-progress");

        var header = new Div(titleLabel, progressLabel);
        header.addClassName("evaluation-run-header");
        return header;
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        this.runId = UUID.fromString(parameter);

        var run = runService.loadRun(runId);
        this.evaluationId = run.getEvaluationId();
        startEvaluation();
    }

    private void startEvaluation() {
        try {
            var response = chatService.startSession(runId);
            progressLabel.setText("Pregunta 1");
            addMessage("evaluador", response.message());
            answerField.focus();
        }
        catch (Exception e) {
            Notification.show("Error al iniciar la actividad formativa: " + e.getMessage());
            backButton.setVisible(true);
        }
    }

    private void onSend() {
        if (complete)
            return;

        var answer = answerField.getValue().trim();
        if (answer.isBlank()) {
            Notification.show("Escribí una respuesta antes de enviar");
            return;
        }

        addMessage("estudiante", answer);
        answerField.clear();
        setInputEnabled(false);
        setLoadingState(true, NEXT_QUESTION_LOADING_TEXT);

        var ui = getUI().orElse(null);
        if (ui == null)
            return;

        new Thread(() -> {
            try {
                var response = chatService.processAnswer(runId, answer);
                ui.access(() -> {
                    setLoadingState(false, NEXT_QUESTION_LOADING_TEXT);
                    if (response.type() == EvaluationChatService.TurnType.QUESTION) {
                        addMessage("evaluador", response.message());
                        setInputEnabled(true);
                        answerField.focus();
                        var session = chatService.getSession(runId);
                        if (session != null) {
                            progressLabel.setText("Pregunta %d".formatted(session.questions.size()));
                        }
                    }
                    else {
                        complete = true;
                        progressLabel.setText("Actividad formativa completada");
                        setLoadingState(true, REPORT_LOADING_TEXT);
                        navigateToCompletedEvaluation();
                    }
                });
            }
            catch (Exception e) {
                ui.access(() -> {
                    setLoadingState(false, NEXT_QUESTION_LOADING_TEXT);
                    Notification.show("Error: " + e.getMessage());
                    setInputEnabled(true);
                });
            }
        }).start();
    }

    private void navigateToCompletedEvaluation() {
        if (evaluationId == null) {
            setLoadingState(false, NEXT_QUESTION_LOADING_TEXT);
            backButton.removeThemeVariants(ButtonVariant.TERTIARY);
            backButton.addThemeVariants(ButtonVariant.PRIMARY);
            backButton.setVisible(true);
            progressLabel.setText("Actividad formativa completada — presioná Volver para salir");
            return;
        }

        getUI().ifPresent(
            ui -> ui.navigate(
                EvaluationView.class,
                QueryParameters.of(EvaluationView.OPEN_EVALUATION_QUERY_PARAMETER, evaluationId.toString())));
    }

    private void addMessage(String sender, String text) {
        var bubble = new Div();
        bubble.addClassName("evaluation-chat-bubble");
        bubble.addClassName("evaluation-chat-" + sender);

        var senderLabel = new Span(sender.equals("evaluador") ? "Evaluador" : "Tú");
        senderLabel.addClassName("evaluation-chat-sender");

        var content = new Span(text);
        content.getElement().getStyle().set("white-space", "pre-wrap");

        bubble.add(senderLabel, content);
        messagesContainer.add(bubble);

        scrollToBottom();
    }

    private void setInputEnabled(boolean enabled) {
        answerField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
    }

    private void setLoadingState(boolean visible, String text) {
        loadingLabel.setText(text);
        loadingIndicator.setVisible(visible);
    }

    private void scrollToBottom() {
        scrollRegion.getElement().executeJs("setTimeout(() => { this.scrollTop = this.scrollHeight; }, 50);");
    }
}
