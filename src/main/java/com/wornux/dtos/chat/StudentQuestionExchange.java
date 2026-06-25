package com.wornux.dtos.chat;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import com.wornux.ui.conversation.*;

public class StudentQuestionExchange implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(5);

    private final ConversationState state;
    private transient CompletableFuture<StudentQuestionResponse> pendingResponse;

    public StudentQuestionExchange(ConversationState state) {
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
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for student response", exception);
        }
        catch (TimeoutException exception) {
            throw new IllegalStateException("Timed out waiting for student response", exception);
        }
        catch (CancellationException exception) {
            throw new IllegalStateException("Interactive question flow was cancelled", exception);
        }
        catch (ExecutionException exception) {
            throw new IllegalStateException("Failed while waiting for student response", exception.getCause());
        }
        finally {
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
