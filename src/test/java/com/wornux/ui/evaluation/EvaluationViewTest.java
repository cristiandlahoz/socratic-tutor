package com.wornux.ui.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextArea;
import com.wornux.infrastructure.web.BrowserClientService;
import com.wornux.services.evaluation.DiagnosticContinuationDecision;
import com.wornux.services.evaluation.DiagnosticSessionVm;
import com.wornux.services.evaluation.CurrentModeTurnVm;
import com.wornux.services.evaluation.EvaluationGuideArtifactVm;
import com.wornux.services.evaluation.EvaluationQuestionVm;
import com.wornux.services.evaluation.EvaluationResultArtifactVm;
import com.wornux.services.evaluation.EvaluationService;
import com.wornux.services.evaluation.EvaluationTargetVm;
import com.wornux.services.evaluation.EvaluationAttemptVm;
import com.wornux.services.evaluation.PublishEvaluationVm;
import com.wornux.services.evaluation.PublishLifecycleState;
import com.wornux.ui.chat.ChatState;
import java.time.Instant;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ViewPackages(classes = EvaluationView.class)
class EvaluationViewTest extends BrowserlessTest {

  @Mock private EvaluationService evaluationService;
  @Mock private BrowserClientService browserClientService;

  private ChatState chatState;
  private EvaluationView view;

  @BeforeEach
  void setUpView() {
    chatState = new ChatState();
    when(browserClientService.resolveClientId()).thenReturn(UUID.randomUUID());
    when(evaluationService.attempts(any(), any())).thenReturn(List.of());
    when(evaluationService.publishedGuides(any())).thenReturn(List.of());
    when(evaluationService.resultHistory(any())).thenReturn(List.of());
    view = new EvaluationView(evaluationService, chatState, browserClientService);
    UI.getCurrent().add(view);
    view.beforeEnter(null);
  }

  @Test
  void showsPublishFeedbackAndPreventsDuplicateWhileInProgress() {
    var evaluationId = UUID.randomUUID();
    when(evaluationService.defaultEvaluationId()).thenReturn(evaluationId);
    when(evaluationService.resolveEvaluationTarget(evaluationId))
        .thenReturn(new EvaluationTargetVm(evaluationId, "fondocyt", "diagnostico-fondocyt"));
    when(evaluationService.publishEvaluationRevisionWithLifecycle(any(), any(), any(), any(), any(), any()))
        .thenReturn(new PublishEvaluationVm(PublishLifecycleState.IN_PROGRESS, null, null, null));

    view.beforeEnter(null);

    click("Publicar guías");
    click("Publicar guías");

    verify(evaluationService, times(1))
        .publishEvaluationRevisionWithLifecycle(
            eq("fondocyt"), eq("diagnostico-fondocyt"), any(), any(), any(), any());
    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("Publicación en progreso"));
  }

  @Test
  void rendersEmptyCatalogAndGuideInspectionError() {
    var guideId = UUID.randomUUID();
    var evalId = UUID.randomUUID();
    when(evaluationService.defaultEvaluationId()).thenReturn(evalId);
    when(evaluationService.publishedGuides(evalId))
        .thenReturn(
            List.of(new EvaluationGuideArtifactVm(guideId, evalId, UUID.randomUUID(), "contenido", Instant.now())));
    when(evaluationService.guideDetail(evalId, guideId))
        .thenThrow(new IllegalArgumentException("not found"));

    view.beforeEnter(null);
    invokeInspectGuide(guideId);

    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("No se pudo abrir la guía"));

    when(evaluationService.publishedGuides(evalId)).thenReturn(List.of());
    view.beforeEnter(null);
    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("No hay guías publicadas"));
  }

  @Test
  void rendersGuideCatalogAsThreeColumnGridAndShowsInspectedContent() {
    var guideId = UUID.randomUUID();
    var revisionId = UUID.randomUUID();
    var evalId = UUID.randomUUID();
    when(evaluationService.defaultEvaluationId()).thenReturn(evalId);
    when(evaluationService.publishedGuides(evalId))
        .thenReturn(List.of(new EvaluationGuideArtifactVm(guideId, evalId, revisionId, "contenido", Instant.now())));
    when(evaluationService.guideDetail(evalId, guideId))
        .thenReturn(new EvaluationGuideArtifactVm(guideId, evalId, revisionId, "Guía detallada", Instant.now()));

    view.beforeEnter(null);

    assertThat($(Grid.class).all()).hasSize(1);
    assertThat($(Grid.class).all().getFirst().getColumns()).hasSize(3);

    invokeInspectGuide(guideId);

    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("Guía detallada"));
  }

  @Test
  void supportsSingleActiveQuestionAndCompletedContinuationRejectionMessage() {
    var attemptId = UUID.randomUUID();
    var questionId = UUID.randomUUID();
    var evaluationId = UUID.randomUUID();
    var q1 =
        new CurrentModeTurnVm(
            questionId,
            "Primera pregunta",
            new CurrentModeTurnVm.AnswerConstraints(10),
            "Respondé con texto libre.");
    var q2 =
        new CurrentModeTurnVm(
            UUID.randomUUID(),
            "Segunda pregunta",
            new CurrentModeTurnVm.AnswerConstraints(10),
            "Respondé con texto libre.");

    when(evaluationService.defaultEvaluationId()).thenReturn(evaluationId);
    when(evaluationService.resolveEvaluationTarget(evaluationId))
        .thenReturn(new EvaluationTargetVm(evaluationId, "fondocyt", "diagnostico-fondocyt"));
    when(evaluationService.startDiagnosticSession(any(), any()))
        .thenReturn(
            new DiagnosticSessionVm(
                attemptId, "RUNNING", 0, 5, q1, null, DiagnosticContinuationDecision.CONTINUE));
    when(evaluationService.continueDiagnosticSession(attemptId, questionId, "respuesta uno"))
        .thenReturn(
            new DiagnosticSessionVm(
                attemptId, "RUNNING", 1, 5, q2, null, DiagnosticContinuationDecision.CONTINUE));
    doThrow(new IllegalStateException("Diagnostic session is not active"))
        .when(evaluationService)
        .continueDiagnosticSession(attemptId, q2.attemptQuestionId(), "respuesta dos");

    click("Lanzar diagnóstico");
    verify(evaluationService).startDiagnosticSession(any(), eq(evaluationId));
    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("Primera pregunta"));
    setAnswer("Tu respuesta", "respuesta uno");
    click("Responder y continuar");
    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("Segunda pregunta"));

    setAnswer("Tu respuesta", "respuesta dos");
    click("Responder y continuar");
    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("not active"));
  }

  @Test
  void rejectsShortAnswerInUiBeforeCallingService() {
    var attemptId = UUID.randomUUID();
    var questionId = UUID.randomUUID();
    var evaluationId = UUID.randomUUID();
    var q1 =
        new CurrentModeTurnVm(
            questionId,
            "Primera pregunta",
            new CurrentModeTurnVm.AnswerConstraints(10),
            "Respondé con texto libre.");

    when(evaluationService.defaultEvaluationId()).thenReturn(evaluationId);
    when(evaluationService.startDiagnosticSession(any(), any()))
        .thenReturn(
            new DiagnosticSessionVm(
                attemptId, "RUNNING", 0, 5, q1, null, DiagnosticContinuationDecision.CONTINUE));

    click("Lanzar diagnóstico");
    setAnswer("Tu respuesta", "corta");
    click("Responder y continuar");

    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("al menos 10 caracteres"));
    verify(evaluationService, never()).continueDiagnosticSession(any(), any(), any());
  }

  @Test
  void clickingRunningAttemptReopensGuidedEditableFlow() {
    var attemptId = UUID.randomUUID();
    var questionId = UUID.randomUUID();
    var evaluationId = UUID.randomUUID();
    var runningQuestion =
        new EvaluationQuestionVm(questionId, "q1", "bp", 1, "topic", "easy", "Pregunta vieja", List.of());
    var runningAttempt =
        new EvaluationAttemptVm(attemptId, UUID.randomUUID(), "RUNNING", null, List.of(runningQuestion), Map.of());
    var activeQuestion =
        new EvaluationQuestionVm(
            UUID.randomUUID(), "q2", "bp", 2, "topic", "medium", "Pregunta activa real", List.of());

    when(evaluationService.defaultEvaluationId()).thenReturn(evaluationId);
    when(evaluationService.attempts(any(), any())).thenReturn(List.of(runningAttempt));
    when(evaluationService.activeDiagnosticSession(attemptId))
        .thenReturn(
            new DiagnosticSessionVm(
                attemptId,
                "RUNNING",
                1,
                5,
                new CurrentModeTurnVm(
                    activeQuestion.attemptQuestionId(),
                    activeQuestion.prompt(),
                    new CurrentModeTurnVm.AnswerConstraints(10),
                    "Respondé con texto libre."),
                null,
                DiagnosticContinuationDecision.CONTINUE));

    view.beforeEnter(null);
    click(attemptId.toString().substring(0, 8) + " · RUNNING");

    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("Sesión activa"));
    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("Pregunta activa real"));
    assertThat(allParagraphTexts()).noneMatch(text -> text.contains("Pregunta vieja"));
    assertThat($(TextArea.class).all().stream().anyMatch(candidate -> "Tu respuesta".equals(candidate.getLabel())))
        .isTrue();
  }

  @Test
  void inspectGuideShowsGeneratedQuestionsWithOrdinalWhenAvailable() {
    var guideId = UUID.randomUUID();
    var revisionId = UUID.randomUUID();
    var evalId = UUID.randomUUID();
    when(evaluationService.defaultEvaluationId()).thenReturn(evalId);
    when(evaluationService.publishedGuides(evalId))
        .thenReturn(List.of(new EvaluationGuideArtifactVm(guideId, evalId, revisionId, "contenido", Instant.now())));
    when(evaluationService.guideDetail(evalId, guideId))
        .thenReturn(new EvaluationGuideArtifactVm(guideId, evalId, revisionId, "Guía detallada", Instant.now()));
    when(evaluationService.attempts(any(), any()))
        .thenReturn(
            List.of(
                new EvaluationAttemptVm(
                    UUID.randomUUID(),
                    revisionId,
                    "RUNNING",
                    null,
                    List.of(
                        new EvaluationQuestionVm(
                            UUID.randomUUID(),
                            "q1",
                            "bp",
                            1,
                            "topic",
                            "easy",
                            "¿Qué sabés sobre fracciones?",
                            List.of()),
                        new EvaluationQuestionVm(
                            UUID.randomUUID(),
                            "q2",
                            "bp",
                            2,
                            "topic",
                            "medium",
                            "¿Cómo resolvés 1/2 + 1/3?",
                            List.of())),
                    Map.of())));

    view.beforeEnter(null);
    invokeInspectGuide(guideId);

    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("1. ¿Qué sabés sobre fracciones?"));
    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("2. ¿Cómo resolvés 1/2 + 1/3?"));
  }

  @Test
  void rendersResultHistoryAndExplicitNoHistoryMessage() {
    var evaluationId = UUID.randomUUID();
    when(evaluationService.defaultEvaluationId()).thenReturn(evaluationId);
    when(evaluationService.resultHistory(evaluationId))
        .thenReturn(
            List.of(
                new EvaluationResultArtifactVm(
                    UUID.randomUUID(),
                    evaluationId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    Instant.now(),
                    Map.of("summary", "ok"))));

    view.beforeEnter(null);
    assertThat(allParagraphTexts()).anyMatch(text -> text.contains("Resultado"));

    when(evaluationService.resultHistory(evaluationId)).thenReturn(List.of());
    view.beforeEnter(null);
    assertThat(allParagraphTexts())
        .anyMatch(text -> text.contains("No hay resultados históricos para esta evaluación."));
  }

  private void click(String buttonText) {
    var button =
        $(Button.class).all().stream()
            .filter(candidate -> buttonText.equals(candidate.getText()))
            .findFirst()
            .orElseThrow();
    button.click();
  }

  private void setAnswer(String label, String value) {
    var answer =
        $(TextArea.class).all().stream()
            .filter(candidate -> label.equals(candidate.getLabel()))
            .findFirst()
            .orElseThrow();
    answer.setValue(value);
  }

  private List<String> allParagraphTexts() {
    return $(Paragraph.class).all().stream().map(Paragraph::getText).toList();
  }

  private void invokeInspectGuide(UUID guideId) {
    try {
      var method = EvaluationView.class.getDeclaredMethod("inspectGuide", UUID.class);
      method.setAccessible(true);
      method.invoke(view, guideId);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private static String shortId(UUID id) {
    return id.toString().substring(0, 8);
  }
}
