package com.wornux.presentation.evaluation;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.application.evaluation.EvaluationAttemptVm;
import com.wornux.application.evaluation.EvaluationGenerationException;
import com.wornux.application.evaluation.EvaluationQuestionVm;
import com.wornux.application.evaluation.EvaluationService;
import com.wornux.infrastructure.web.BrowserClientService;
import com.wornux.presentation.MainLayout;
import com.wornux.presentation.chat.ChatUiState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Route(value = "evaluations", layout = MainLayout.class)
@PageTitle("Evaluaciones")
public class EvaluationView extends Composite<Div> implements BeforeEnterObserver {

  private final EvaluationService evaluationService;
  private final ChatUiState chatUiState;
  private final BrowserClientService browserClientService;
  private final Div attemptList = new Div();
  private final Div questionList = new Div();
  private final Div reportPanel = new Div();
  private final TextArea teacherInstructions = new TextArea("Guías de evaluación");
  private final TextArea teacherExamples = new TextArea("Ejemplos del profesor");
  private final Map<UUID, TextArea> answers = new LinkedHashMap<>();
  private EvaluationAttemptVm activeAttempt;

  public EvaluationView(
      EvaluationService evaluationService,
      ChatUiState chatUiState,
      BrowserClientService browserClientService) {
    this.evaluationService = evaluationService;
    this.chatUiState = chatUiState;
    this.browserClientService = browserClientService;

    var root = getContent();
    root.addClassName("evaluation-view");
    root.add(createHeader(), createTeacherPanel(), createWorkspace());
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    ensureClientId();
    refreshAttempts();
  }

  private Div createHeader() {
    var eyebrow = new Span("Modo evaluación");
    eyebrow.addClassName("evaluation-eyebrow");

    var title = new H2("Diagnóstico del estudiante");
    title.addClassName("evaluation-title");

    var description =
        new Paragraph(
            "Lanza una evaluación versionada, captura respuestas y genera un reporte persistente"
                + " basado en evidencia.");
    description.addClassName("evaluation-description");

    var launchButton = new Button("Lanzar diagnóstico", new Icon(VaadinIcon.PLAY));
    launchButton.addThemeVariants(ButtonVariant.PRIMARY);
    launchButton.addClassName("evaluation-primary-action");
    launchButton.setAriaLabel("Lanzar diagnóstico inicial");
    launchButton.addClickListener(_ -> launchEvaluation());

    var copy = new Div(eyebrow, title, description);
    copy.addClassName("evaluation-header-copy");

    var header = new Div(copy, launchButton);
    header.addClassName("evaluation-header");
    return header;
  }

  private Div createWorkspace() {
    attemptList.addClassName("evaluation-panel");
    questionList.addClassName("evaluation-panel");
    reportPanel.addClassName("evaluation-panel");

    var layout = new Div(attemptList, questionList, reportPanel);
    layout.addClassName("evaluation-workspace");
    return layout;
  }

  private Div createTeacherPanel() {
    teacherInstructions.setWidthFull();
    teacherInstructions.setMinHeight("7rem");
    teacherInstructions.setValue(
        "Genera preguntas diagnósticas personalizadas. Evalúa razonamiento observable y no asumas nivel global.");
    teacherExamples.setWidthFull();
    teacherExamples.setMinHeight("6rem");
    teacherExamples.setPlaceholder("Un ejemplo por línea. Son guías, no preguntas finales.");

    var publish = new Button("Publicar guías", new Icon(VaadinIcon.CHECK_CIRCLE));
    publish.addThemeVariants(ButtonVariant.PRIMARY);
    publish.addClassName("evaluation-primary-action");
    publish.setAriaLabel("Publicar guías de evaluación");
    publish.addClickListener(_ -> publishGuidelines());

    var panel = new Div(teacherInstructions, teacherExamples, publish);
    panel.addClassName("evaluation-panel");
    return panel;
  }

  private void publishGuidelines() {
    var examples =
        teacherExamples.getValue() == null
            ? List.<String>of()
            : teacherExamples.getValue().lines().filter(line -> !line.isBlank()).toList();
    evaluationService.publishDefaultEvaluationRevision(teacherInstructions.getValue(), examples);
    Notification.show("Guías publicadas para la próxima evaluación");
  }

  private void launchEvaluation() {
    ensureClientId();
    try {
      activeAttempt =
          evaluationService.launchEvaluation(
              chatUiState.clientId().peek(), evaluationService.defaultEvaluationId());
      renderActiveAttempt();
      refreshAttempts();
    } catch (EvaluationGenerationException exception) {
      Notification.show("No se pudo generar la evaluación. Revisa las guías o intenta de nuevo.");
    }
  }

  private void submitAttempt() {
    if (activeAttempt == null) {
      return;
    }
    for (var question : activeAttempt.questions()) {
      var textArea = answers.get(question.attemptQuestionId());
      evaluationService.submitResponse(
          question.attemptQuestionId(),
          textArea == null ? "" : textArea.getValue(),
          List.of());
    }
    activeAttempt = evaluationService.submitAttempt(activeAttempt.attemptId());
    activeAttempt = evaluationService.gradeAttempt(activeAttempt.attemptId());
    Notification.show("Evaluación enviada y reporte generado");
    renderActiveAttempt();
    refreshAttempts();
  }

  private void refreshAttempts() {
    attemptList.removeAll();
    var title = new Span("Intentos");
    title.addClassName("evaluation-section-title");
    attemptList.add(title);

    var attempts =
        evaluationService.attempts(
            chatUiState.clientId().peek(), null);
    if (attempts.isEmpty()) {
      var empty = new Paragraph("Todavía no hay evaluaciones para esta sesión.");
      empty.addClassName("evaluation-description");
      attemptList.add(empty);
      return;
    }
    for (var attempt : attempts) {
      var button =
          new Button(
              "%s · %s".formatted(shortId(attempt.attemptId()), attempt.status()),
              _ -> {
                activeAttempt = attempt;
                renderActiveAttempt();
              });
      button.addThemeVariants(ButtonVariant.TERTIARY);
      button.addClassName("evaluation-attempt-button");
      button.setWidthFull();
      attemptList.add(button);
    }
  }

  private void renderActiveAttempt() {
    questionList.removeAll();
    reportPanel.removeAll();
    answers.clear();
    if (activeAttempt == null) {
      return;
    }

    var heading = new Span("Preguntas");
    heading.addClassName("evaluation-section-title");
    questionList.add(heading);

    for (var question : activeAttempt.questions()) {
      questionList.add(questionCard(question));
    }

    var submit = new Button("Revisar y enviar", new Icon(VaadinIcon.CHECK));
    submit.addThemeVariants(ButtonVariant.PRIMARY);
    submit.addClassName("evaluation-primary-action");
    submit.setAriaLabel("Revisar y enviar evaluación");
    submit.setEnabled("IN_PROGRESS".equals(activeAttempt.status()));
    submit.addClickListener(_ -> submitAttempt());
    questionList.add(submit);

    renderReport();
  }

  private Div questionCard(EvaluationQuestionVm question) {
    var topic = new Span("%d · %s · %s".formatted(question.ordinal(), question.topicKey(), question.difficulty()));
    topic.addClassName("evaluation-question-meta");

    var prompt = new Paragraph(question.prompt());
    prompt.addClassName("evaluation-question-prompt");

    var answer = new TextArea("Respuesta");
    answer.setWidthFull();
    answer.setMinHeight("8rem");
    answer.setValueChangeMode(ValueChangeMode.EAGER);
    answer.setAriaLabel("Respuesta para " + question.questionKey());
    answer.setEnabled("IN_PROGRESS".equals(activeAttempt.status()));
    answers.put(question.attemptQuestionId(), answer);

    var card = new Div(topic, prompt, answer);
    card.addClassName("evaluation-question-card");
    return card;
  }

  private void renderReport() {
    var heading = new Span("Reporte");
    heading.addClassName("evaluation-section-title");
    reportPanel.add(heading);

    if (!"GRADED".equals(activeAttempt.status())) {
      var pending = new Paragraph("El reporte aparecerá después de enviar y calificar.");
      pending.addClassName("evaluation-description");
      reportPanel.add(pending);
      return;
    }

    var score = new Span(activeAttempt.score() == null ? "Sin puntuación" : activeAttempt.score() + " / 100");
    score.addClassName("evaluation-score");
    var feedback = new Paragraph(String.valueOf(activeAttempt.feedback().getOrDefault("summary", "")));
    feedback.addClassName("evaluation-description");
    reportPanel.add(score, feedback, reportList("Fortalezas", "strengths"), reportList("Debilidades", "weakConcepts"), reportList("Misconceptions", "activeMisconceptions"));
  }

  private Div reportList(String label, String key) {
    var title = new Span(label);
    title.addClassName("evaluation-report-subtitle");
    var values = activeAttempt.feedback().get(key);
    var body = new Paragraph(values == null ? "Sin evidencia suficiente." : String.valueOf(values));
    body.addClassName("evaluation-description");
    var section = new Div(title, body);
    section.addClassName("evaluation-report-section");
    return section;
  }

  private void ensureClientId() {
    if (chatUiState.clientId().peek() == null) {
      chatUiState.clientId().set(browserClientService.resolveClientId());
    }
  }

  private static String shortId(UUID id) {
    return id == null ? "" : id.toString().substring(0, 8);
  }
}
