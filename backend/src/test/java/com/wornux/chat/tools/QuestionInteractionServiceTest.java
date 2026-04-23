package com.wornux.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.chat.questions.StudentQuestion;
import com.wornux.chat.questions.StudentQuestionAnswer;
import com.wornux.chat.questions.StudentQuestionOption;
import com.wornux.chat.questions.StudentQuestionResponse;
import com.wornux.chat.questions.StudentQuestionSet;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class QuestionInteractionServiceTest {

  private final QuestionInteractionService service = new QuestionInteractionService();

  @Test
  void ask_questions_exposes_pending_state_and_records_completed_response() throws Exception {
    var routing =
        new QuestionInteractionService.QuestionRouting(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    var questionSet = sampleQuestionSet();

    var executor = Executors.newSingleThreadExecutor();
    try {
      CompletableFuture<StudentQuestionResponse> future =
          CompletableFuture.supplyAsync(
              () -> service.askQuestions(routing, questionSet, Duration.ofSeconds(2)), executor);

      awaitPending(routing);
      assertThat(service.findPending(routing.clientId(), routing.conversationId()))
          .hasValueSatisfying(pending -> assertThat(pending.questionSet()).isEqualTo(questionSet));

      var response =
          new StudentQuestionResponse(
              List.of(new StudentQuestionAnswer("q1", List.of("Loops"), "Con for me pierdo")));
      service.submitResponse(routing.clientId(), routing.conversationId(), response);

      assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo(response);
      assertThat(service.drainCompletedResponses(routing.turnId()))
          .singleElement()
          .satisfies(completed -> assertThat(completed.response()).isEqualTo(response));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void submit_response_rejects_empty_answers() throws Exception {
    var routing =
        new QuestionInteractionService.QuestionRouting(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    var questionSet = sampleQuestionSet();

    var executor = Executors.newSingleThreadExecutor();
    try {
      CompletableFuture.runAsync(
          () -> service.askQuestions(routing, questionSet, Duration.ofSeconds(2)), executor);
      awaitPending(routing);

      assertThatThrownBy(
              () ->
                  service.submitResponse(
                      routing.clientId(),
                      routing.conversationId(),
                      new StudentQuestionResponse(
                          List.of(new StudentQuestionAnswer("q1", List.of(), "")))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("at least one selected option or custom text");
    } finally {
      executor.shutdownNow();
    }
  }

  private StudentQuestionSet sampleQuestionSet() {
    return new StudentQuestionSet(
        "Diagnostico",
        "clarification",
        StudentQuestionSet.ProfileImpact.NONE,
        List.of(
            new StudentQuestion(
                "q1",
                "Tema",
                "Que tema te cuesta mas ahora?",
                List.of(
                    new StudentQuestionOption("Loops", "Bucles y trazas"),
                    new StudentQuestionOption("Arrays", "Indices y recorridos")),
                false)));
  }

  private void awaitPending(QuestionInteractionService.QuestionRouting routing)
      throws InterruptedException {
    for (int attempt = 0; attempt < 20; attempt++) {
      if (service.findPending(routing.clientId(), routing.conversationId()).isPresent()) {
        return;
      }
      TimeUnit.MILLISECONDS.sleep(25);
    }
    throw new AssertionError("Pending question interaction was not published in time");
  }
}
