package com.wornux.ui.evaluation;

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
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.services.evaluation.EvaluationAttemptVm;
import com.wornux.services.evaluation.EvaluationGuideArtifactVm;
import com.wornux.services.evaluation.EvaluationGenerationException;
import com.wornux.services.evaluation.EvaluationQuestionVm;
import com.wornux.services.evaluation.EvaluationResultArtifactVm;
import com.wornux.services.evaluation.EvaluationService;
import com.wornux.services.evaluation.EvaluationTargetVm;
import com.wornux.services.evaluation.CurrentModeTurnVm;
import com.wornux.services.evaluation.PublishLifecycleState;
import com.wornux.infrastructure.web.BrowserClientService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.chat.ChatState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Route(value = "evaluations", layout = MainLayout.class)
@PageTitle("Evaluaciones")
public class EvaluationView extends Composite<Div> implements BeforeEnterObserver {

  private static final Logger log = LoggerFactory.getLogger(EvaluationView.class);
  private static final int MIN_ANSWER_LENGTH = 10;

  private final EvaluationService evaluationService;
  private final ChatState chatUiState;
  private final BrowserClientService browserClientService;
  private final Div attemptList = new Div();
  private final Div questionList = new Div();
  private final Div reportPanel = new Div();
  private final Div publishStatusPanel = new Div();
  private final Div guideCatalogPanel = new Div();
  private final Div guideDetailPanel = new Div();
  private final Div diagnosticStatusPanel = new Div();
  private final Div historyPanel = new Div();
  private final TextArea teacherInstructions = new TextArea("Guías de evaluación");
  private final TextArea teacherExamples = new TextArea("Ejemplos del profesor");
  private final TextArea activeAnswerInput = new TextArea("Tu respuesta");
  private final Button publishButton = new Button("Publicar guías", new Icon(VaadinIcon.CHECK_CIRCLE));
  private final Button continueDiagnosticButton = new Button("Responder y continuar", new Icon(VaadinIcon.ARROW_RIGHT));
  private final Grid<EvaluationGuideArtifactVm> guideCatalogGrid = new Grid<>(EvaluationGuideArtifactVm.class, false);
  private final Map<UUID, TextArea> answers = new LinkedHashMap<>();
  private EvaluationAttemptVm activeAttempt;
  private PublishLifecycleState publishLifecycleState;
  private UUID selectedEvaluationId;
  private UUID activeDiagnosticAttemptId;
  private CurrentModeTurnVm activeCurrentModeTurn;

  public EvaluationView(
      EvaluationService evaluationService,
      ChatState chatUiState,
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
    launchButton.addClickListener(_ -> startDiagnosticSession());

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

    publishButton.addThemeVariants(ButtonVariant.PRIMARY);
    publishButton.addClassName("evaluation-primary-action");
    publishButton.setAriaLabel("Publicar guías de evaluación");
    publishButton.addClickListener(_ -> publishGuidelines());
    continueDiagnosticButton.addClickListener(_ -> continueDiagnosticSession());

    publishStatusPanel.addClassName("evaluation-status-panel");
    guideCatalogPanel.addClassName("evaluation-status-panel");
    guideDetailPanel.addClassName("evaluation-status-panel");
    historyPanel.addClassName("evaluation-status-panel");

    configureGuideCatalogGrid();

    var panel =
        new Div(
            teacherInstructions,
            teacherExamples,
            publishButton,
            publishStatusPanel,
            guideCatalogPanel,
            guideDetailPanel,
            historyPanel);
    panel.addClassName("evaluation-panel");
    return panel;
  }

  private void configureGuideCatalogGrid() {
    guideCatalogGrid.addColumn(guide -> shortId(guide.guideArtifactId())).setHeader("Guía").setAutoWidth(true);
    guideCatalogGrid.addColumn(guide -> shortId(guide.revisionId())).setHeader("Revisión").setAutoWidth(true);
    guideCatalogGrid
        .addColumn(
            new ComponentRenderer<>(
                guide -> {
                  var inspect = new Button("Inspeccionar");
                  inspect.addClickListener(_ -> inspectGuide(guide.guideArtifactId()));
                  return inspect;
                }))
        .setHeader("Inspeccionar")
        .setAutoWidth(true);
    guideCatalogGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
    guideCatalogGrid.setWidthFull();
    guideCatalogGrid.setAllRowsVisible(true);
  }

  private void publishGuidelines() {
    if (publishLifecycleState == PublishLifecycleState.IN_PROGRESS) {
      renderPublishStatus();
      return;
    }
    var examples =
        teacherExamples.getValue() == null
            ? List.<String>of()
            : teacherExamples.getValue().lines().filter(line -> !line.isBlank()).toList();
    publishLifecycleState = PublishLifecycleState.IN_PROGRESS;
    renderPublishStatus();
    try {
      var evaluationTarget = currentEvaluationTarget();
      var publishResult =
          evaluationService.publishEvaluationRevisionWithLifecycle(
              evaluationTarget.subjectSlug(),
              evaluationTarget.evaluationSlug(),
              teacherInstructions.getValue(),
              Map.of("allowFreeText", true, "showReviewBeforeSubmit", true),
              Map.of("profileEvidenceOnly", true),
              examples);
      publishLifecycleState = publishResult.state();
      if (publishResult.state() == PublishLifecycleState.COMPLETED) {
        Notification.show(
            "Guías publicadas. Artifact: " + shortId(publishResult.guideArtifactId()));
      }
    } catch (RuntimeException exception) {
      log.error("Failed to publish evaluation guidelines for evaluationId={}", selectedEvaluationId, exception);
      publishLifecycleState = PublishLifecycleState.FAILED;
    }
    renderPublishStatus();
    refreshGuideCatalog();
    refreshResultHistory();
  }

  private void renderPublishStatus() {
    publishStatusPanel.removeAll();
    publishButton.setEnabled(publishLifecycleState != PublishLifecycleState.IN_PROGRESS);
    String label =
        publishLifecycleState == null
            ? "Publicación en espera"
            : switch (publishLifecycleState) {
              case IN_PROGRESS -> "Publicación en progreso...";
              case COMPLETED -> "Publicación completada";
              case FAILED -> "Publicación fallida";
            };
    publishStatusPanel.add(new Paragraph(label));
  }

  private void startDiagnosticSession() {
    ensureClientId();
    try {
      if (selectedEvaluationId == null) {
        selectedEvaluationId = evaluationService.defaultEvaluationId();
      }
      var session =
          evaluationService.startDiagnosticSession(chatUiState.clientId().peek(), selectedEvaluationId);
      activeDiagnosticAttemptId = session.attemptId();
      activeCurrentModeTurn = session.currentTurn();
      renderDiagnosticState(session.status(), session.completionReason());
      refreshAttempts();
      refreshResultHistory();
    } catch (EvaluationGenerationException exception) {
      log.error(
          "Failed to launch diagnostic session for evaluationId={} clientId={}",
          selectedEvaluationId,
          chatUiState.clientId().peek(),
          exception);
      Notification.show("No se pudo generar la evaluación. Revisa las guías o intenta de nuevo.");
    }
  }

  private EvaluationTargetVm currentEvaluationTarget() {
    if (selectedEvaluationId == null) {
      selectedEvaluationId = evaluationService.defaultEvaluationId();
    }
    return evaluationService.resolveEvaluationTarget(selectedEvaluationId);
  }

  private void continueDiagnosticSession() {
    if (activeDiagnosticAttemptId == null || activeCurrentModeTurn == null) {
      return;
    }
    var answer = activeAnswerInput.getValue() == null ? "" : activeAnswerInput.getValue().trim();
    if (answer.length() < MIN_ANSWER_LENGTH) {
      renderDiagnosticState("RUNNING", "La respuesta debe tener al menos 10 caracteres.");
      return;
    }
    try {
      var session =
          evaluationService.continueDiagnosticSession(
              activeDiagnosticAttemptId,
              activeCurrentModeTurn.attemptQuestionId(),
              answer);
      activeCurrentModeTurn = session.currentTurn();
      activeAnswerInput.clear();
      renderDiagnosticState(session.status(), session.completionReason());
      refreshResultHistory();
    } catch (IllegalStateException exception) {
      renderDiagnosticState("COMPLETED", exception.getMessage());
    }
  }

  private void renderDiagnosticState(String status, String message) {
    questionList.removeAll();
    diagnosticStatusPanel.removeAll();
    diagnosticStatusPanel.addClassName("evaluation-status-panel");

    var heading = new Span("Diagnóstico guiado");
    heading.addClassName("evaluation-section-title");
    questionList.add(heading, diagnosticStatusPanel);

    var statusText =
        status != null && status.equals("COMPLETED")
            ? "Sesión completada"
            : "Sesión activa: responde la pregunta actual";
    diagnosticStatusPanel.add(new Paragraph(statusText));

    if (message != null && !message.isBlank()) {
      diagnosticStatusPanel.add(new Paragraph("Detalle: " + message));
    }

    if (activeCurrentModeTurn != null && !"COMPLETED".equals(status)) {
      questionList.add(new Paragraph(activeCurrentModeTurn.activeQuestion()));
      questionList.add(new Paragraph(activeCurrentModeTurn.continuationHint()));
      activeAnswerInput.setWidthFull();
      activeAnswerInput.setMinHeight("8rem");
      activeAnswerInput.setAriaLabel("Respuesta activa de diagnóstico");
      continueDiagnosticButton.addThemeVariants(ButtonVariant.PRIMARY);
      continueDiagnosticButton.setEnabled(true);
      questionList.add(activeAnswerInput, continueDiagnosticButton);
    } else {
      continueDiagnosticButton.setEnabled(false);
      questionList.add(new Paragraph("No hay preguntas activas para esta sesión."));
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
      if (selectedEvaluationId == null) {
        selectedEvaluationId = evaluationService.defaultEvaluationId();
      }
      refreshGuideCatalog();
      refreshResultHistory();
      return;
    }
    for (var attempt : attempts) {
      if (selectedEvaluationId == null) {
        selectedEvaluationId = evaluationService.defaultEvaluationId();
      }
      var button =
          new Button(
              "%s · %s".formatted(shortId(attempt.attemptId()), attempt.status()),
              _ -> {
                activeAttempt = attempt;
                if ("RUNNING".equals(attempt.status())) {
                  activeDiagnosticAttemptId = attempt.attemptId();
                  var activeSession = evaluationService.activeDiagnosticSession(attempt.attemptId());
                  activeCurrentModeTurn = activeSession.currentTurn();
                  renderDiagnosticState(activeSession.status(), activeSession.completionReason());
                  return;
                }
                renderActiveAttempt();
              });
      button.addThemeVariants(ButtonVariant.TERTIARY);
      button.addClassName("evaluation-attempt-button");
      button.setWidthFull();
      attemptList.add(button);
    }
    refreshGuideCatalog();
    refreshResultHistory();
  }

  private void refreshGuideCatalog() {
    guideCatalogPanel.removeAll();
    var title = new Span("Catálogo de guías");
    title.addClassName("evaluation-section-title");
    guideCatalogPanel.add(title);
    if (selectedEvaluationId == null) {
      selectedEvaluationId = evaluationService.defaultEvaluationId();
    }
    List<EvaluationGuideArtifactVm> guides;
    try {
      guides = evaluationService.publishedGuides(selectedEvaluationId);
    } catch (RuntimeException exception) {
      guideCatalogPanel.add(new Paragraph("Error al cargar catálogo de guías"));
      return;
    }
    if (guides.isEmpty()) {
      guideCatalogPanel.add(new Paragraph("No hay guías publicadas todavía."));
      return;
    }
    guideCatalogGrid.setItems(guides);
    guideCatalogPanel.add(guideCatalogGrid);
  }

  private void inspectGuide(UUID guideId) {
    guideDetailPanel.removeAll();
    guideDetailPanel.add(new Paragraph("Cargando guía..."));
    try {
      var detail = evaluationService.guideDetail(selectedEvaluationId, guideId);
      guideDetailPanel.removeAll();
      guideDetailPanel.add(
          new Span("Guía seleccionada: " + shortId(detail.guideArtifactId())),
          new Paragraph(detail.guideContent()));
      var generatedQuestions =
          evaluationService.attempts(chatUiState.clientId().peek(), null).stream()
              .filter(attempt -> detail.revisionId().equals(attempt.evaluationRevisionId()))
              .flatMap(attempt -> attempt.questions().stream())
              .sorted(java.util.Comparator.comparingInt(EvaluationQuestionVm::ordinal))
              .map(question -> question.ordinal() + ". " + question.prompt())
              .distinct()
              .toList();
      if (!generatedQuestions.isEmpty()) {
        guideDetailPanel.add(new Paragraph("Preguntas generadas"));
        for (var line : generatedQuestions) {
          guideDetailPanel.add(new Paragraph(line));
        }
      }
    } catch (RuntimeException exception) {
      guideDetailPanel.removeAll();
      guideDetailPanel.add(new Paragraph("No se pudo abrir la guía seleccionada."));
    }
  }

  private void refreshResultHistory() {
    historyPanel.removeAll();
    historyPanel.add(new Span("Historial de resultados"));
    if (selectedEvaluationId == null) {
      return;
    }
    List<EvaluationResultArtifactVm> history;
    try {
      history = evaluationService.resultHistory(selectedEvaluationId);
    } catch (RuntimeException exception) {
      historyPanel.add(new Paragraph("No se pudo recuperar el historial."));
      return;
    }
    if (history.isEmpty()) {
      historyPanel.add(new Paragraph("No hay resultados históricos para esta evaluación."));
      return;
    }
    for (var result : history) {
      historyPanel.add(
          new Paragraph(
              "Resultado " + shortId(result.resultArtifactId()) + " · intento " + shortId(result.attemptId())));
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
