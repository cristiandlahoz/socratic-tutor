package com.wornux.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.wornux.chat.questions.StudentQuestion;
import com.wornux.chat.questions.StudentQuestionAnswer;
import com.wornux.chat.questions.StudentQuestionOption;
import com.wornux.chat.questions.StudentQuestionResponse;
import com.wornux.chat.questions.StudentQuestionSet;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class StudentQuestionExchangeTest {

  @Test
  void publishesPendingQuestionAndCompletesWhenStudentSubmits() throws Exception {
    var state = new ChatUiState();
    var exchange = new StudentQuestionExchange(state);
    var questionSet = sampleQuestionSet();
    var response =
        new StudentQuestionResponse(
            List.of(new StudentQuestionAnswer("q1", List.of("Voy bien"), "quiero validar")));
    var executor = Executors.newSingleThreadExecutor();

    try {
      var pending = CompletableFuture.supplyAsync(() -> exchange.ask(questionSet), executor);

      awaitPendingQuestion(state);
      assertThat(state.pendingQuestionSet().peek()).isSameAs(questionSet);

      assertThat(exchange.submit(response)).isTrue();

      assertThat(pending.get()).isEqualTo(response);
      assertThat(state.pendingQuestionSet().peek()).isNull();
      assertThat(state.questionSubmissionInProgress().peek()).isFalse();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void rejectsSecondPendingQuestion() throws Exception {
    var state = new ChatUiState();
    var exchange = new StudentQuestionExchange(state);
    var executor = Executors.newSingleThreadExecutor();

    try {
      var first = CompletableFuture.supplyAsync(() -> exchange.ask(sampleQuestionSet()), executor);
      awaitPendingQuestion(state);

      assertThatIllegalStateException()
          .isThrownBy(() -> exchange.ask(sampleQuestionSet()))
          .withMessage("There is already a pending interactive question flow");

      exchange.cancelPending();
      first.exceptionally(_ -> null).get();
    } finally {
      executor.shutdownNow();
    }
  }

  private void awaitPendingQuestion(ChatUiState state) throws InterruptedException {
    var deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (state.pendingQuestionSet().peek() == null && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertThat(state.pendingQuestionSet().peek()).isNotNull();
  }

  private StudentQuestionSet sampleQuestionSet() {
    return new StudentQuestionSet(
        "Antes de seguir",
        "diagnosis",
        StudentQuestionSet.ProfileImpact.NONE,
        List.of(
            new StudentQuestion(
                "q1",
                "Confianza",
                "Como te sientes con este tema?",
                List.of(
                    new StudentQuestionOption("Muy perdido", "Necesito empezar de cero"),
                    new StudentQuestionOption("Voy bien", "Solo quiero validar detalles")),
                false)));
  }
}
