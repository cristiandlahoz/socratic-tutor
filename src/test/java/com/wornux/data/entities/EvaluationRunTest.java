package com.wornux.data.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.wornux.data.enums.EvaluationRunStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluationRunTest {

  @Test
  void createInitializesInProgressRun() {
    var evaluationId = UUID.randomUUID();
    var studentClientId = UUID.randomUUID();
    var questionsJson = "[{\"questionText\":\"What is a variable?\",\"questionKey\":\"q1\"}]";

    var run = EvaluationRun.create(evaluationId, studentClientId, questionsJson);

    assertNotNull(run.getId());
    assertEquals(evaluationId, run.getEvaluationId());
    assertEquals(studentClientId, run.getStudentClientId());
    assertEquals(questionsJson, run.getQuestionsAskedJson());
    assertEquals(EvaluationRunStatus.IN_PROGRESS, run.getStatus());
    assertNotNull(run.getCreatedAt());
    assertNotNull(run.getUpdatedAt());
  }

  @Test
  void createInitializesEmptyAnswersAndReport() {
    var run = EvaluationRun.create(UUID.randomUUID(), UUID.randomUUID(), "[]");

    assertNotNull(run.getQuestionsAskedJson());
    assertEquals("[]", run.getQuestionsAskedJson());
  }
}
