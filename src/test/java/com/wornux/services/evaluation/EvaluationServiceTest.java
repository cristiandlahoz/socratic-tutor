package com.wornux.services.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.wornux.data.entities.Evaluation;
import com.wornux.data.entities.EvaluationAttempt;
import com.wornux.data.entities.EvaluationAttemptQuestion;
import com.wornux.data.entities.EvaluationRevision;
import com.wornux.data.entities.Subject;
import com.wornux.data.entities.SubjectConfigRevision;
import com.wornux.data.enums.EvaluationStatus;
import com.wornux.data.repositories.evaluation.EvaluationAttemptQuestionRepository;
import com.wornux.data.repositories.evaluation.EvaluationAttemptRepository;
import com.wornux.data.repositories.evaluation.EvaluationAttemptResponseRepository;
import com.wornux.data.repositories.evaluation.EvaluationGuideArtifactRepository;
import com.wornux.data.repositories.evaluation.EvaluationQuestionExampleRepository;
import com.wornux.data.repositories.evaluation.EvaluationRepository;
import com.wornux.data.repositories.evaluation.EvaluationResultArtifactRepository;
import com.wornux.data.repositories.evaluation.EvaluationRevisionRepository;
import com.wornux.data.repositories.subject.SubjectConfigRevisionRepository;
import com.wornux.domain.profile.StudentLearningProfile;
import com.wornux.domain.profile.StudentProfileSnapshot;
import com.wornux.services.profile.StudentProfileService;
import com.wornux.services.subject.SubjectConfig;
import com.wornux.services.subject.SubjectConfigService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationServiceTest {

  @Mock private EvaluationRepository evaluationRepository;
  @Mock private EvaluationRevisionRepository revisionRepository;
  @Mock private EvaluationAttemptRepository attemptRepository;
  @Mock private EvaluationAttemptQuestionRepository attemptQuestionRepository;
  @Mock private EvaluationAttemptResponseRepository responseRepository;
  @Mock private EvaluationQuestionExampleRepository exampleRepository;
  @Mock private EvaluationGuideArtifactRepository guideArtifactRepository;
  @Mock private EvaluationResultArtifactRepository resultArtifactRepository;
  @Mock private SubjectConfigRevisionRepository subjectConfigRevisionRepository;
  @Mock private EvaluationQuestionGenerationService questionGenerationService;
  @Mock private StudentProfileService studentProfileService;
  @Mock private SubjectConfigService subjectConfigService;
  @Mock private ChatModel chatModel;

  private EvaluationService service;

  @BeforeEach
  void setUp() {
    service =
        new EvaluationService(
            evaluationRepository,
            revisionRepository,
            attemptRepository,
            attemptQuestionRepository,
            responseRepository,
            exampleRepository,
            guideArtifactRepository,
            resultArtifactRepository,
            subjectConfigRevisionRepository,
            questionGenerationService,
            studentProfileService,
            subjectConfigService,
            chatModel,
            new SocraticFreeTextModePolicy(),
            new ObjectMapper());
  }

  @Test
  void publishLifecycleReturnsCompletedAndPersistsGuideArtifact() {
    var subjectSlug = "matematica";
    var evaluationSlug = "algebra";
    var evaluation = mockPublishedEvaluation(subjectSlug, evaluationSlug);
    var revision = mockRevision(evaluation, 1);

    when(evaluationRepository.findBySubject_SlugAndSlug(subjectSlug, evaluationSlug))
        .thenReturn(Optional.of(evaluation));
    when(subjectConfigService.current(subjectSlug))
        .thenReturn(new SubjectConfig(UUID.randomUUID(), subjectSlug, "Matematica", 1, UUID.randomUUID(), Map.of(), Map.of(), Map.of()));
    var subjectConfigRevision = mockSubjectConfigRevision();
    when(subjectConfigRevisionRepository.findById(any())).thenReturn(Optional.of(subjectConfigRevision));
    when(revisionRepository.findFirstByEvaluationOrderByVersionDesc(evaluation)).thenReturn(Optional.of(revision));
    when(revisionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(revisionRepository.findById(any())).thenReturn(Optional.of(revision));
    when(guideArtifactRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result =
        service.publishEvaluationRevisionWithLifecycle(
            subjectSlug,
            evaluationSlug,
            "Publish instructions",
            Map.of(),
            Map.of(),
            List.of("example"));

    assertThat(result.state()).isEqualTo(PublishLifecycleState.COMPLETED);
    assertThat(result.revisionId()).isNotNull();
    assertThat(result.guideArtifactId()).isNotNull();
  }

  @Test
  void publishLifecycleReturnsFailedWhenPublicationThrows() {
    when(evaluationRepository.findBySubject_SlugAndSlug("subject", "eval")).thenReturn(Optional.empty());

    var result =
        service.publishEvaluationRevisionWithLifecycle(
            "subject", "eval", "instructions", Map.of(), Map.of(), List.of());

    assertThat(result.state()).isEqualTo(PublishLifecycleState.FAILED);
    assertThat(result.errorMessage()).contains("Unknown evaluation");
  }

  @Test
  void publishLifecyclePreventsDuplicateAttemptWhileInProgress() throws Exception {
    var subjectSlug = "matematica";
    var evaluationSlug = "algebra";
    var evaluation = mockPublishedEvaluation(subjectSlug, evaluationSlug);
    var revision = mockRevision(evaluation, 1);
    var enteredSave = new CountDownLatch(1);
    var releaseSave = new CountDownLatch(1);

    when(evaluationRepository.findBySubject_SlugAndSlug(subjectSlug, evaluationSlug))
        .thenReturn(Optional.of(evaluation));
    when(subjectConfigService.current(subjectSlug))
        .thenReturn(new SubjectConfig(UUID.randomUUID(), subjectSlug, "Matematica", 1, UUID.randomUUID(), Map.of(), Map.of(), Map.of()));
    var subjectConfigRevision = mockSubjectConfigRevision();
    when(subjectConfigRevisionRepository.findById(any())).thenReturn(Optional.of(subjectConfigRevision));
    when(revisionRepository.findFirstByEvaluationOrderByVersionDesc(evaluation)).thenReturn(Optional.of(revision));
    when(revisionRepository.save(any()))
        .thenAnswer(
            invocation -> {
              enteredSave.countDown();
              releaseSave.await(3, TimeUnit.SECONDS);
              return invocation.getArgument(0);
            });
    when(revisionRepository.findById(any())).thenReturn(Optional.of(revision));
    when(guideArtifactRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var executor = Executors.newSingleThreadExecutor();
    var firstFuture =
        executor.submit(
            () ->
                service.publishEvaluationRevisionWithLifecycle(
                    subjectSlug, evaluationSlug, "instructions", Map.of(), Map.of(), List.of("x")));
    assertThat(enteredSave.await(2, TimeUnit.SECONDS)).isTrue();

    var duplicate =
        service.publishEvaluationRevisionWithLifecycle(
            subjectSlug, evaluationSlug, "instructions", Map.of(), Map.of(), List.of("x"));
    assertThat(duplicate.state()).isEqualTo(PublishLifecycleState.IN_PROGRESS);

    releaseSave.countDown();
    assertThat(firstFuture.get(3, TimeUnit.SECONDS).state()).isEqualTo(PublishLifecycleState.COMPLETED);
    executor.shutdownNow();
  }

  @Test
  void startDiagnosticSessionReturnsSingleActiveQuestion() {
    var clientId = UUID.randomUUID();
    var evaluationId = UUID.randomUUID();
    var attemptId = UUID.randomUUID();
    var evaluation = mockPublishedEvaluation("math", "algebra");
    var revision = mockRevision(evaluation, 1);
    var generated =
        new GeneratedEvaluationQuestion(
            "q1", "bp1", 1, "topic", "easy", "Question 1", List.of("a", "b"), Map.of(), Map.of(), List.of());
    var attempt = mockAttemptWithStatus("RUNNING");
    when(attempt.getEvaluationRevision()).thenReturn(revision);
    when(attempt.getId()).thenReturn(attemptId);
    var firstQuestion = mockQuestion(attemptId, 1, "Question 1");
    var answeredQuestion = org.mockito.Mockito.mock(EvaluationAttemptQuestion.class);
    when(answeredQuestion.getId()).thenReturn(UUID.randomUUID());
    when(answeredQuestion.getOrdinal()).thenReturn(0);
    when(answeredQuestion.getQuestionKey()).thenReturn("q0");
    when(answeredQuestion.getBlueprintKey()).thenReturn("bp0");
    when(answeredQuestion.getQuestionSnapshot())
        .thenReturn(Map.of("prompt", "Already answered", "options", List.of()));
    when(answeredQuestion.getResponses()).thenReturn(List.of(org.mockito.Mockito.mock(com.wornux.data.entities.EvaluationAttemptResponse.class)));
    when(firstQuestion.getResponses()).thenReturn(List.of());
    when(attempt.getQuestions()).thenReturn(List.of(answeredQuestion, firstQuestion));
    when(attempt.getCompletionReason()).thenReturn(null);

    when(evaluationRepository.findWithCurrentRevisionById(evaluationId)).thenReturn(Optional.of(evaluation));
    when(evaluation.getCurrentRevision()).thenReturn(revision);
    when(revisionRepository.findWithExamplesById(revision.getId())).thenReturn(Optional.of(revision));
    when(revision.getQuestionExamples()).thenReturn(List.of());
    when(subjectConfigService.current(any()))
        .thenReturn(new SubjectConfig(UUID.randomUUID(), "math", "Math", 1, UUID.randomUUID(), Map.of(), Map.of(), Map.of()));
    when(questionGenerationService.generate(any(), any(), any(), any())).thenReturn(List.of(generated));
    when(studentProfileService.load(clientId))
        .thenReturn(new StudentProfileSnapshot("es", null, false, List.of(), 1L, StudentLearningProfile.empty("es")));
    when(attemptRepository.save(any())).thenReturn(attempt);
    when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

    var session = service.startDiagnosticSession(clientId, evaluationId);

    assertThat(session.continuationDecision()).isEqualTo(DiagnosticContinuationDecision.CONTINUE);
    assertThat(session.currentTurn()).isNotNull();
    assertThat(session.currentTurn().attemptQuestionId()).isEqualTo(firstQuestion.getId());
    assertThat(session.currentTurn().activeQuestion()).isEqualTo("Question 1");
    assertThat(session.currentTurn().answerConstraints().minChars()).isEqualTo(10);
  }

  @Test
  void activeDiagnosticSessionUsesFirstUnansweredQuestionInsteadOfLowestOrdinal() {
    var attemptId = UUID.randomUUID();
    var attempt = mockAttemptWithStatus("RUNNING");
    var evaluation = mockPublishedEvaluation("math", "algebra");
    var revision = mockRevision(evaluation, 1);
    when(attempt.getEvaluationRevision()).thenReturn(revision);

    var answeredLowestOrdinal = org.mockito.Mockito.mock(EvaluationAttemptQuestion.class);
    when(answeredLowestOrdinal.getId()).thenReturn(UUID.randomUUID());
    when(answeredLowestOrdinal.getOrdinal()).thenReturn(1);
    when(answeredLowestOrdinal.getQuestionKey()).thenReturn("q1");
    when(answeredLowestOrdinal.getBlueprintKey()).thenReturn("bp1");
    when(answeredLowestOrdinal.getQuestionSnapshot())
        .thenReturn(Map.of("prompt", "Primera (respondida)", "options", List.of()));
    when(answeredLowestOrdinal.getResponses())
        .thenReturn(List.of(org.mockito.Mockito.mock(com.wornux.data.entities.EvaluationAttemptResponse.class)));

    var activeUnanswered = org.mockito.Mockito.mock(EvaluationAttemptQuestion.class);
    when(activeUnanswered.getId()).thenReturn(UUID.randomUUID());
    when(activeUnanswered.getOrdinal()).thenReturn(2);
    when(activeUnanswered.getQuestionKey()).thenReturn("q2");
    when(activeUnanswered.getBlueprintKey()).thenReturn("bp2");
    when(activeUnanswered.getQuestionSnapshot())
        .thenReturn(Map.of("prompt", "Segunda (activa)", "options", List.of()));
    when(activeUnanswered.getResponses()).thenReturn(List.of());

    when(attempt.getQuestions()).thenReturn(List.of(answeredLowestOrdinal, activeUnanswered));
    when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

    var session = service.activeDiagnosticSession(attemptId);

    assertThat(session.status()).isEqualTo("RUNNING");
    assertThat(session.currentTurn()).isNotNull();
    assertThat(session.currentTurn().attemptQuestionId()).isEqualTo(activeUnanswered.getId());
  }

  @Test
  void continueDiagnosticRejectsCompletedSessionProgression() {
    var attempt = mockAttemptWithStatus("COMPLETED");
    var attemptId = UUID.randomUUID();

    when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

    assertThatThrownBy(() -> service.continueDiagnosticSession(attemptId, UUID.randomUUID(), "respuesta"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not active");
  }

  @Test
  void continueDiagnosticCompletesAtMaxQuestions() {
    var attemptId = UUID.randomUUID();
    var questionId = UUID.randomUUID();
    var evaluation = mockPublishedEvaluation("math", "algebra");
    var revision = mockRevision(evaluation, 1);
    when(revision.getSettings()).thenReturn(Map.of("maxQuestions", 1));
    var attempt = mockAttemptWithStatus("RUNNING");
    when(attempt.getEvaluationRevision()).thenReturn(revision);

    var question = org.mockito.Mockito.mock(EvaluationAttemptQuestion.class);
    when(question.getId()).thenReturn(questionId);
    when(question.getResponses()).thenReturn(List.of());
    when(attemptQuestionRepository.findByAttemptOrderByOrdinalAsc(attempt)).thenReturn(List.of(question));
    when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
    when(attemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var session = service.continueDiagnosticSession(attemptId, questionId, "final answer");

    assertThat(session.continuationDecision())
        .isEqualTo(DiagnosticContinuationDecision.COMPLETE_MAX_QUESTIONS);
    verify(resultArtifactRepository).save(any());
  }

  @Test
  void continueDiagnosticGeneratesSingleNextQuestionWhenContinuing() {
    var attemptId = UUID.randomUUID();
    var questionId = UUID.randomUUID();
    var evaluation = mockPublishedEvaluation("math", "algebra");
    var revision = mockRevision(evaluation, 1);
    when(revision.getSettings()).thenReturn(Map.of("maxQuestions", 3));
    when(revision.getQuestionExamples()).thenReturn(List.of());

    var attempt = mockAttemptWithStatus("RUNNING");
    when(attempt.getEvaluationRevision()).thenReturn(revision);
    when(attempt.getClientId()).thenReturn(UUID.randomUUID());

    var currentQuestion = org.mockito.Mockito.mock(EvaluationAttemptQuestion.class);
    when(currentQuestion.getId()).thenReturn(questionId);
    when(currentQuestion.getResponses()).thenReturn(List.of());
    when(currentQuestion.getOrdinal()).thenReturn(1);
    var generatedQuestion = org.mockito.Mockito.mock(EvaluationAttemptQuestion.class);
    when(generatedQuestion.getOrdinal()).thenReturn(2);
    when(generatedQuestion.getId()).thenReturn(UUID.randomUUID());
    when(generatedQuestion.getQuestionKey()).thenReturn("q2");
    when(generatedQuestion.getBlueprintKey()).thenReturn("bp2");
    when(generatedQuestion.getQuestionSnapshot()).thenReturn(Map.of("prompt", "Next?", "options", List.of()));
    when(attemptQuestionRepository.findByAttemptOrderByOrdinalAsc(attempt))
        .thenReturn(List.of(currentQuestion), List.of(currentQuestion, generatedQuestion));
    when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

    when(subjectConfigService.current(any()))
        .thenReturn(new SubjectConfig(UUID.randomUUID(), "math", "Math", 1, UUID.randomUUID(), Map.of(), Map.of(), Map.of()));
    when(studentProfileService.load(any()))
        .thenReturn(new StudentProfileSnapshot("es", null, false, List.of(), 1L, StudentLearningProfile.empty("es")));

    var next =
        new GeneratedEvaluationQuestion(
            "q2", "bp2", 2, "topic", "medium", "Next?", List.of("a", "b"), Map.of(), Map.of(), List.of());
    when(questionGenerationService.generateNextQuestion(any(), any(), any(), any(), any(CurrentModeTurnContext.class)))
        .thenReturn(next);
    when(attemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var session = service.continueDiagnosticSession(attemptId, questionId, "respuesta valida");

    assertThat(session.continuationDecision()).isEqualTo(DiagnosticContinuationDecision.CONTINUE);
    assertThat(session.currentTurn()).isNotNull();
    assertThat(session.currentTurn().activeQuestion()).isEqualTo("Next?");
    verify(attempt)
        .addGeneratedQuestion(
            any(),
            any(),
            any(),
            any(Integer.class),
            argThat(
                snapshot -> {
                  var generatedAt = snapshot.get("generatedAt");
                  if (!(generatedAt instanceof String text)) {
                    return false;
                  }
                  var options = snapshot.get("options");
                  if (!(options instanceof List<?> list) || !list.isEmpty()) {
                    return false;
                  }
                  Instant.parse(text);
                  return true;
                }),
            any());
    verify(questionGenerationService)
        .generateNextQuestion(
            any(),
            any(),
            any(),
            any(),
            argThat(
                context ->
                    "SOCRATIC_FREE_TEXT".equals(context.mode())
                        && context.answeredCount() == 1
                        && context.nextOrdinal() == 2
                        && context.maxQuestions() == 3
                        && "CONTINUE".equals(context.completionIntent())));
    verify(resultArtifactRepository, never()).save(any());
  }

  @Test
  void continueDiagnosticRejectsAnswerShorterThanTenCharacters() {
    var attemptId = UUID.randomUUID();
    var questionId = UUID.randomUUID();
    var attempt = mockAttemptWithStatus("RUNNING");

    var question = org.mockito.Mockito.mock(EvaluationAttemptQuestion.class);
    when(question.getId()).thenReturn(questionId);
    when(question.getResponses()).thenReturn(List.of());
    when(attemptQuestionRepository.findByAttemptOrderByOrdinalAsc(attempt)).thenReturn(List.of(question));
    when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
    var revision = mockRevision(mockPublishedEvaluation("math", "algebra"), 1);
    when(attempt.getEvaluationRevision()).thenReturn(revision);

    assertThatThrownBy(() -> service.continueDiagnosticSession(attemptId, questionId, "corta"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 10 characters");

    verify(responseRepository, never()).save(any());
  }

  @Test
  void resolveEvaluationTargetReturnsSubjectAndEvaluationSlugs() {
    var evaluationId = UUID.randomUUID();
    var evaluation = mockPublishedEvaluation("fondocyt", "diagnostico-fondocyt");
    when(evaluation.getId()).thenReturn(evaluationId);
    when(evaluationRepository.findById(evaluationId)).thenReturn(Optional.of(evaluation));

    var target = service.resolveEvaluationTarget(evaluationId);

    assertThat(target.evaluationId()).isEqualTo(evaluationId);
    assertThat(target.subjectSlug()).isEqualTo("fondocyt");
    assertThat(target.evaluationSlug()).isEqualTo("diagnostico-fondocyt");
  }

  @Test
  void resolveEvaluationTargetThrowsForUnknownEvaluation() {
    var evaluationId = UUID.randomUUID();
    when(evaluationRepository.findById(evaluationId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resolveEvaluationTarget(evaluationId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown evaluation");
  }

  private Evaluation mockPublishedEvaluation(String subjectSlug, String evaluationSlug) {
    var subject = org.mockito.Mockito.mock(Subject.class);
    when(subject.getSlug()).thenReturn(subjectSlug);
    var evaluation = org.mockito.Mockito.mock(Evaluation.class);
    when(evaluation.getSubject()).thenReturn(subject);
    when(evaluation.getSlug()).thenReturn(evaluationSlug);
    when(evaluation.getStatus()).thenReturn(EvaluationStatus.PUBLISHED);
    when(evaluation.getId()).thenReturn(UUID.randomUUID());
    return evaluation;
  }

  private EvaluationRevision mockRevision(Evaluation evaluation, long version) {
    var revision = org.mockito.Mockito.mock(EvaluationRevision.class);
    when(revision.getVersion()).thenReturn(version);
    when(revision.getEvaluation()).thenReturn(evaluation);
    when(revision.getId()).thenReturn(UUID.randomUUID());
    when(revision.getInstructions()).thenReturn("instructions");
    when(revision.getSettings()).thenReturn(Map.of());
    when(revision.getRubric()).thenReturn(Map.of());
    var subjectConfigRevision = mockSubjectConfigRevision();
    when(revision.getSubjectConfigRevision()).thenReturn(subjectConfigRevision);
    return revision;
  }

  private SubjectConfigRevision mockSubjectConfigRevision() {
    var subject = org.mockito.Mockito.mock(Subject.class);
    when(subject.getSlug()).thenReturn("math");
    var subjectConfigRevision = org.mockito.Mockito.mock(SubjectConfigRevision.class);
    when(subjectConfigRevision.getId()).thenReturn(UUID.randomUUID());
    when(subjectConfigRevision.getSubject()).thenReturn(subject);
    return subjectConfigRevision;
  }

  private EvaluationAttempt mockAttemptWithStatus(String status) {
    var attempt = org.mockito.Mockito.mock(EvaluationAttempt.class);
    when(attempt.getId()).thenReturn(UUID.randomUUID());
    when(attempt.getStatus()).thenReturn(com.wornux.data.enums.EvaluationAttemptStatus.valueOf(status));
    return attempt;
  }

  private EvaluationAttemptQuestion mockQuestion(UUID id, int ordinal, String prompt) {
    var question = org.mockito.Mockito.mock(EvaluationAttemptQuestion.class);
    when(question.getId()).thenReturn(id);
    when(question.getOrdinal()).thenReturn(ordinal);
    when(question.getQuestionKey()).thenReturn("q" + ordinal);
    when(question.getBlueprintKey()).thenReturn("bp" + ordinal);
    when(question.getQuestionSnapshot()).thenReturn(Map.of("prompt", prompt, "options", List.of("a", "b")));
    return question;
  }
}
