package com.wornux.data.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.wornux.data.enums.EvaluationStatus;
import org.junit.jupiter.api.Test;

class EvaluationTest {

  @Test
  void createInitializesDraftEvaluation() {
    var evaluation = Evaluation.create("Loops quiz", "Assess loop tracing");

    assertNotNull(evaluation.getId());
    assertEquals("Loops quiz", evaluation.getTitle());
    assertEquals("Assess loop tracing", evaluation.getInstruction());
    assertEquals(EvaluationStatus.DRAFT, evaluation.getStatus());
    assertNull(evaluation.getQuestionsJson());
    assertNull(evaluation.getAnswersJson());
    assertNull(evaluation.getReportMarkdown());
    assertNotNull(evaluation.getCreatedAt());
    assertNotNull(evaluation.getUpdatedAt());
  }

  @Test
  void transitionsUpdateStoredPayloadsAndStatus() {
    var evaluation = Evaluation.create("Tracing", "Ask one question");

    evaluation.markGeneratingQuestions();
    evaluation.saveQuestions("[{\"id\":1}]");
    evaluation.markAnswering();
    evaluation.saveAnswers("[{\"id\":1,\"answer\":\"42\"}]");
    evaluation.markGeneratingReport();
    evaluation.completeReport("# Report");

    assertEquals("[{\"id\":1}]", evaluation.getQuestionsJson());
    assertEquals("[{\"id\":1,\"answer\":\"42\"}]", evaluation.getAnswersJson());
    assertEquals("# Report", evaluation.getReportMarkdown());
    assertEquals(EvaluationStatus.COMPLETED, evaluation.getStatus());
  }
}
