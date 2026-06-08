package com.wornux.services.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.data.entities.EvaluationRun;
import com.wornux.data.enums.EvaluationRunStatus;
import com.wornux.data.repositories.evaluation.EvaluationRunRepository;
import com.wornux.services.evaluation.EvaluationChatService.AnswerRecord;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationRunServiceTest {

  @Mock private EvaluationRunRepository runRepository;

  @Mock private EvaluationQuestionGenerationService questionGenerationService;

  private EvaluationRunService runService;

  @BeforeEach
  void setUp() {
    runService = new EvaluationRunService(runRepository, questionGenerationService);
  }

  @Test
  void createRunPersistsAndReturns() {
    var evaluationId = UUID.randomUUID();
    var clientId = UUID.randomUUID();
    var questionsJson = "[{\"questionText\":\"Q1\",\"questionKey\":\"q1\"}]";

    when(runRepository.save(any(EvaluationRun.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, EvaluationRun.class));

    var run = runService.createRun(evaluationId, clientId, questionsJson);

    assertNotNull(run.getId());
    assertEquals(evaluationId, run.getEvaluationId());
    assertEquals(clientId, run.getStudentClientId());
    assertEquals(questionsJson, run.getQuestionsAskedJson());
    assertEquals(EvaluationRunStatus.IN_PROGRESS, run.getStatus());
    verify(runRepository).save(any(EvaluationRun.class));
  }

  @Test
  void loadRunFailsForUnknown() {
    var runId = UUID.randomUUID();
    when(runRepository.findById(runId)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> runService.loadRun(runId));
  }

  @Test
  void loadRunReturnsFound() {
    var run = EvaluationRun.create(UUID.randomUUID(), UUID.randomUUID(), "[]");
    var runId = run.getId();

    when(runRepository.findById(runId)).thenReturn(Optional.of(run));

    var found = runService.loadRun(runId);

    assertEquals(runId, found.getId());
  }

  @Test
  void appendAnswerAddsToExisting() {
    var run = EvaluationRun.create(UUID.randomUUID(), UUID.randomUUID(), "[]");
    var runId = run.getId();
    run.setAnswersGivenJson("[]");

    when(runRepository.findById(runId)).thenReturn(Optional.of(run));

    var answer = new AnswerRecord("q1", "What is a variable?", "A storage location");
    var updated = runService.appendAnswer(runId, answer);

    assertEquals(EvaluationRunStatus.IN_PROGRESS, updated.getStatus());
    assertNotNull(updated.getAnswersGivenJson());
  }

  @Test
  void completeReportSetsCompletedStatus() {
    var run = EvaluationRun.create(UUID.randomUUID(), UUID.randomUUID(), "[]");
    var runId = run.getId();

    when(runRepository.findById(runId)).thenReturn(Optional.of(run));

    var updated = runService.completeReport(runId, "# Report");

    assertEquals(EvaluationRunStatus.COMPLETED, updated.getStatus());
    assertEquals("# Report", updated.getReportMarkdown());
  }

  @Test
  void markFailedSetsFailedStatus() {
    var run = EvaluationRun.create(UUID.randomUUID(), UUID.randomUUID(), "[]");
    var runId = run.getId();

    when(runRepository.findById(runId)).thenReturn(Optional.of(run));

    var updated = runService.markFailed(runId);

    assertEquals(EvaluationRunStatus.FAILED, updated.getStatus());
  }
}
