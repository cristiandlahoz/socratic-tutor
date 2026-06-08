package com.wornux.ui.evaluation;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.wornux.services.evaluation.EvaluationChatService;
import com.wornux.services.evaluation.EvaluationRunService;
import com.wornux.ui.chat.BrailleSpinner;
import java.util.UUID;

@Route(value = "evaluations/run", layout = com.wornux.ui.MainLayout.class)
public class EvaluationChatView extends Composite<Div> implements HasUrlParameter<String> {

  private final EvaluationRunService runService;
  private final EvaluationChatService chatService;

  private UUID runId;
  private final Div titleLabel = new Div();
  private final Span progressLabel = new Span();
  private final Div messagesContainer = new Div();
  private final TextArea answerField = new TextArea();
  private final Button sendButton = new Button(new Icon(VaadinIcon.ARROW_UP));
  private final Button backButton = new Button("Volver a evaluaciones", new Icon(VaadinIcon.ARROW_LEFT));
  private final Div scrollRegion = new Div();
  private final BrailleSpinner loadingSpinner = new BrailleSpinner();
  private final Span loadingLabel = new Span();
  private final Div loadingIndicator = new Div(loadingSpinner, loadingLabel);
  private boolean complete = false;
  private boolean pendingBackNav = false;
  private Dialog completionDialog;
  private static final String NEXT_QUESTION_LOADING_TEXT = "Formulando la siguiente pregunta...";
  private static final String REPORT_LOADING_TEXT = "Generando reporte evaluativo...";

  public EvaluationChatView(
      EvaluationRunService runService,
      EvaluationChatService chatService) {
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
    answerField.getElement().addEventListener("keydown", _ -> {})
        .setFilter("event.key === 'Enter' && !event.shiftKey")
        .preventDefault();
    // keyup: submit on Enter (value IS committed by keyup; EAGER mode keeps it synced)
    answerField.getElement().addEventListener("keyup", _ -> onSend())
        .setFilter("event.key === 'Enter' && !event.shiftKey");

    var composer = new Div(answerField, sendButton);
    composer.addClassName("chat-composer-wrap");

    var inputArea = new Div(loadingIndicator, composer);
    inputArea.addClassName("chat-composer-shell");

    backButton.addThemeVariants(ButtonVariant.TERTIARY);
    backButton.setVisible(false);
    backButton.addClickListener(_ ->
        getUI().ifPresent(ui -> ui.navigate(EvaluationView.class)));

    content.add(header, scrollRegion, inputArea, backButton);
  }

  private Div buildHeader() {
    titleLabel.addClassName("evaluation-run-title");
    titleLabel.setText("Evaluación en curso");

    progressLabel.addClassName("evaluation-run-progress");

    var header = new Div(titleLabel, progressLabel);
    header.addClassName("evaluation-run-header");
    return header;
  }

  @Override
  public void setParameter(BeforeEvent event, String parameter) {
    this.runId = UUID.fromString(parameter);

    runService.loadRun(runId);
    startEvaluation();
  }

  private void startEvaluation() {
    try {
      var response = chatService.startSession(runId);
      progressLabel.setText("Pregunta 1");
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
    setLoadingState(true, NEXT_QUESTION_LOADING_TEXT);

    var ui = getUI().orElse(null);
    if (ui == null) return;

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
          } else {
            complete = true;
            progressLabel.setText("Evaluación completada");
            setLoadingState(true, REPORT_LOADING_TEXT);
            showCompletionDialog(response.reportMarkdown());
            setLoadingState(false, NEXT_QUESTION_LOADING_TEXT);
          }
        });
      } catch (Exception e) {
        ui.access(() -> {
          setLoadingState(false, NEXT_QUESTION_LOADING_TEXT);
          Notification.show("Error: " + e.getMessage());
          setInputEnabled(true);
        });
      }
    }).start();
  }

  private void showCompletionDialog(String reportMarkdown) {
    if (completionDialog != null && completionDialog.isOpened()) return;

    var dialog = new Dialog();
    dialog.setHeaderTitle("Reporte de evaluación");
    dialog.setWidth("min(90vw, 50rem)");
    dialog.setMaxHeight("80vh");
    dialog.setCloseOnOutsideClick(false);
    dialog.setCloseOnEsc(false);

    var content = new Div();
    content.addClassName("evaluation-completion-dialog-content");

    var markdownContainer = new Div();
    markdownContainer.addClassName("evaluation-report-markdown");
    var markdownElement = new Element("vaadin-markdown");
    markdownElement.setProperty("content", reportMarkdown);
    markdownContainer.getElement().appendChild(markdownElement);
    content.add(markdownContainer);

    dialog.add(content);

    var volverButton = new Button("Volver a evaluaciones", e -> {
      pendingBackNav = true;
      dialog.close();
    });
    volverButton.addThemeVariants(ButtonVariant.PRIMARY);

    var cancelButton = new Button("Cancelar", e -> dialog.close());
    cancelButton.addThemeVariants(ButtonVariant.TERTIARY);

    dialog.getFooter().add(cancelButton, volverButton);
    dialog.addOpenedChangeListener(e -> {
      if (!e.isOpened()) {
        if (pendingBackNav) {
          pendingBackNav = false;
          completionDialog = null;
          getUI().ifPresent(ui -> ui.getPage().setLocation("/evaluations"));
        } else {
          completionDialog = null;
          backButton.removeThemeVariants(ButtonVariant.TERTIARY);
          backButton.addThemeVariants(ButtonVariant.PRIMARY);
          backButton.setVisible(true);
          progressLabel.setText("Evaluación completada — presioná Volver para salir");
        }
      }
    });

    completionDialog = dialog;
    dialog.open();
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
    scrollRegion.getElement().executeJs(
        "setTimeout(() => { this.scrollTop = this.scrollHeight; }, 50);");
  }
}
