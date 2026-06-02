package com.wornux.data.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.wornux.data.enums.EvaluationStatus;
import org.junit.jupiter.api.Test;

class EvaluationTest {

  @Test
  void createInitializesPendingEvaluation() {
    var evaluation = Evaluation.create("Loops quiz", "Assess loop tracing");

    assertNotNull(evaluation.getId());
    assertEquals("Loops quiz", evaluation.getTitle());
    assertEquals("Assess loop tracing", evaluation.getInstruction());
    assertEquals(EvaluationStatus.PENDING, evaluation.getStatus());
    assertNull(evaluation.getQuestionsJson());
    assertNull(evaluation.getAnswersJson());
    assertNull(evaluation.getReportMarkdown());
    assertNotNull(evaluation.getCreatedAt());
    assertNotNull(evaluation.getUpdatedAt());
  }
}
