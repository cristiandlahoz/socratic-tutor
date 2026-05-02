package com.wornux.domain.chat;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.chat.questions.StudentQuestionResponse;
import com.wornux.domain.chat.questions.StudentQuestionSet;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class StudentQuestionExchange implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(5);

  private final ChatUiState state;
  private transient CompletableFuture<StudentQuestionResponse> pendingResponse;

  public StudentQuestionExchange(ChatUiState state) {
    this.state = state;
  }

  public StudentQuestionResponse ask(StudentQuestionSet questionSet) {
    CompletableFuture<StudentQuestionResponse> responseFuture;
    synchronized (this) {
      if (pendingResponse != null && !pendingResponse.isDone()) {
        throw new IllegalStateException("There is already a pending interactive question flow");
      }
      responseFuture = new CompletableFuture<>();
      pendingResponse = responseFuture;
      state.questionSubmissionInProgress().set(false);
      state.pendingQuestionSet().set(questionSet);
    }

    try {
      return responseFuture.get(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for student response", exception);
    } catch (TimeoutException exception) {
      throw new IllegalStateException("Timed out waiting for student response", exception);
    } catch (CancellationException exception) {
      throw new IllegalStateException("Interactive question flow was cancelled", exception);
    } catch (ExecutionException exception) {
      throw new IllegalStateException(
          "Failed while waiting for student response", exception.getCause());
    } finally {
      synchronized (this) {
        if (pendingResponse == responseFuture) {
          pendingResponse = null;
        }
      }
      state.clearPendingQuestionState();
    }
  }

  public boolean submit(StudentQuestionResponse response) {
    CompletableFuture<StudentQuestionResponse> responseFuture;
    synchronized (this) {
      responseFuture = pendingResponse;
      if (responseFuture == null || responseFuture.isDone()) {
        return false;
      }
      state.questionSubmissionInProgress().set(true);
    }
    if (!responseFuture.complete(response)) {
      state.questionSubmissionInProgress().set(false);
      return false;
    }
    return true;
  }

  public void cancelPending() {
    CompletableFuture<StudentQuestionResponse> responseFuture;
    synchronized (this) {
      responseFuture = pendingResponse;
      pendingResponse = null;
    }
    if (responseFuture != null) {
      responseFuture.cancel(true);
    }
    state.clearPendingQuestionState();
  }
}
