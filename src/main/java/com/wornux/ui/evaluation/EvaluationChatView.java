package com.wornux.ui.evaluation;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.wornux.services.evaluation.EvaluationChatService;
import com.wornux.services.evaluation.EvaluationChatService.EvaluationTurnResponse;
import com.wornux.services.evaluation.EvaluationChatService.TurnType;
import com.wornux.services.evaluation.EvaluationService;
import java.util.UUID;

@Route(value = "evaluations/run", layout = com.wornux.ui.MainLayout.class)
public class EvaluationChatView extends Composite<Div> implements HasUrlParameter<String> {

  private final EvaluationService evaluationService;
  private final EvaluationChatService chatService;

  private UUID evaluationId;
  private final H2 titleLabel = new H2();
  private final Span progressLabel = new Span();
  private final Div messagesContainer = new Div();
  private final TextField answerField = new TextField();
  private final Button sendButton = new Button("Enviar");
  private final Div inputArea = new Div();
  private final Button backButton = new Button("Volver a evaluaciones");
  private boolean complete = false;

  public EvaluationChatView(
      EvaluationService evaluationService,
      EvaluationChatService chatService) {
    this.evaluationService = evaluationService;
    this.chatService = chatService;

    var content = getContent();
    content.addClassName("evaluation-chat-view");

    var header = buildHeader();
    messagesContainer.addClassName("evaluation-chat-messages");
    messagesContainer.setWidthFull();

    answerField.setPlaceholder("Escribí tu respuesta...");
    answerField.setWidthFull();
    answerField.addKeyDownListener(Key.ENTER, _ -> onSend());

    sendButton.addThemeVariants(ButtonVariant.PRIMARY);
    sendButton.setIcon(new Icon(VaadinIcon.ARROW_RIGHT));
    sendButton.addClickListener(_ -> onSend());

    var inputRow = new HorizontalLayout(answerField, sendButton);
    inputRow.setWidthFull();
    inputRow.setPadding(false);
    inputArea.addClassName("evaluation-chat-input");
    inputArea.add(inputRow);

    backButton.addThemeVariants(ButtonVariant.TERTIARY);
    backButton.setIcon(new Icon(VaadinIcon.ARROW_LEFT));
    backButton.addClickListener(_ -> getUI().ifPresent(ui -> ui.navigate(EvaluationView.class)));
    backButton.setVisible(false);

    var layout = new VerticalLayout(header, messagesContainer, inputArea, backButton);
    layout.setPadding(true);
    layout.setSpacing(true);
    layout.setHeightFull();
    layout.expand(messagesContainer);
    content.add(layout);
  }

  private Div buildHeader() {
    titleLabel.addClassNames("evaluation-chat-title");
    titleLabel.getStyle().set("margin", "0");

    progressLabel.addClassName("evaluation-chat-progress");

    var header = new Div(titleLabel, progressLabel);
    header.addClassName("evaluation-chat-header");
    return header;
  }

  @Override
  public void setParameter(BeforeEvent event, String parameter) {
    this.evaluationId = UUID.fromString(parameter);

    var evaluation = evaluationService.get(evaluationId);
    titleLabel.setText("Evaluación: " + evaluation.getTitle());

    startEvaluation();
  }

  private void startEvaluation() {
    try {
      var response = chatService.startSession(evaluationId);
      progressLabel.setText("Pregunta 1 de ?");
      addMessage("evaluador", response.message());
      answerField.focus();
    } catch (Exception e) {
      Notification.show("Error al iniciar la evaluación: " + e.getMessage());
      backButton.setVisible(true);
    }
  }

  private void onSend() {
    if (complete) return;

    var answer = answerField.getValue().trim();
    if (answer.isBlank()) {
      Notification.show("Escribí una respuesta antes de enviar");
      return;
    }

    addMessage("estudiante", answer);
    answerField.clear();
    setInputEnabled(false);

    try {
      var response = chatService.processAnswer(evaluationId, answer);

      if (response.type() == TurnType.QUESTION) {
        addMessage("evaluador", response.message());
        var session = chatService.getSession(evaluationId);
        if (session != null) {
          progressLabel.setText("Pregunta %d de %d".formatted(
              session.currentIndex + 1, session.questions.size()));
        }
        setInputEnabled(true);
        answerField.focus();
      } else {
        addMessage("evaluador", response.message());
        complete = true;
        progressLabel.setText("Evaluación completada");
        inputArea.setVisible(false);
        backButton.setVisible(true);
      }
    } catch (Exception e) {
      Notification.show("Error al procesar respuesta: " + e.getMessage());
      setInputEnabled(true);
    }
  }

  private void addMessage(String sender, String text) {
    var bubble = new Div();
    bubble.addClassName("evaluation-chat-bubble");
    bubble.addClassName("evaluation-chat-" + sender);

    var senderLabel = new Span(sender.equals("evaluador") ? "🎓 Evaluador" : "👤 Tú");
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

  private void scrollToBottom() {
    getContent().getElement().executeJs(
        "const container = this; setTimeout(() => { const msgs = container.querySelector('.evaluation-chat-messages'); if (msgs) msgs.scrollTop = msgs.scrollHeight; }, 50);");
  }
}
