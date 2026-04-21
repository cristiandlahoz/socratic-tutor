package com.wornux.chat.tools;

import com.wornux.chat.questions.StudentQuestion;
import com.wornux.chat.questions.StudentQuestionAnswer;
import com.wornux.chat.questions.StudentQuestionResponse;
import com.wornux.chat.questions.StudentQuestionSet;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class QuestionInteractionService {

    private final ConcurrentHashMap<InteractionKey, PendingInteraction> pendingByConversation = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<CompletedQuestionInteraction>> completedByTurnId = new ConcurrentHashMap<>();

    public StudentQuestionResponse askQuestions(QuestionRouting routing, StudentQuestionSet questionSet, Duration timeout) {
        var key = new InteractionKey(routing.clientId(), routing.conversationId());
        var pending = new PendingInteraction(routing, questionSet, Instant.now(), new CompletableFuture<>());
        var existing = pendingByConversation.putIfAbsent(key, pending);
        if (existing != null) {
            throw new IllegalStateException("There is already a pending interactive question flow for this conversation");
        }

        try {
            var response = pending.responseFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            recordCompleted(new CompletedQuestionInteraction(routing, questionSet, response, Instant.now()));
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for student response", exception);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Timed out waiting for student response", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed while waiting for student response", exception.getCause());
        } finally {
            pendingByConversation.remove(key, pending);
        }
    }

    public Optional<PendingQuestionView> findPending(UUID clientId, UUID conversationId) {
        if (clientId == null || conversationId == null) {
            return Optional.empty();
        }
        var pending = pendingByConversation.get(new InteractionKey(clientId, conversationId));
        if (pending == null) {
            return Optional.empty();
        }
        return Optional.of(new PendingQuestionView(pending.routing(), pending.questionSet(), pending.createdAt()));
    }

    public void submitResponse(UUID clientId, UUID conversationId, StudentQuestionResponse response) {
        var pending = pendingByConversation.get(new InteractionKey(clientId, conversationId));
        if (pending == null) {
            throw new IllegalStateException("No pending interactive questions for this conversation");
        }
        validateResponse(pending.questionSet(), response);
        if (!pending.responseFuture().complete(response)) {
            throw new IllegalStateException("This interactive question flow was already completed");
        }
    }

    public List<CompletedQuestionInteraction> drainCompletedResponses(UUID turnId) {
        var completed = completedByTurnId.remove(turnId);
        return completed == null ? List.of() : List.copyOf(completed);
    }

    private void recordCompleted(CompletedQuestionInteraction interaction) {
        completedByTurnId.compute(interaction.routing().turnId(), (_, existing) -> {
            var next = existing == null ? new ArrayList<CompletedQuestionInteraction>() : new ArrayList<>(existing);
            next.add(interaction);
            return next;
        });
    }

    private void validateResponse(StudentQuestionSet questionSet, StudentQuestionResponse response) {
        Map<String, StudentQuestion> questionsById = new LinkedHashMap<>();
        for (StudentQuestion question : questionSet.questions()) {
            questionsById.put(question.id(), question);
        }

        Set<String> answeredIds = new LinkedHashSet<>();
        for (StudentQuestionAnswer answer : response.answers()) {
            var question = questionsById.get(answer.questionId());
            if (question == null) {
                throw new IllegalArgumentException("Unexpected answer questionId: " + answer.questionId());
            }
            if (!answeredIds.add(answer.questionId())) {
                throw new IllegalArgumentException("Duplicate answer for questionId: " + answer.questionId());
            }
            if (!answer.hasContent()) {
                throw new IllegalArgumentException("Answers must contain at least one selected option or custom text");
            }
            if (!question.multiSelect() && answer.selectedOptionLabels().size() > 1) {
                throw new IllegalArgumentException("Single-select questions cannot have more than one selected option");
            }
            if (!question.allowCustomText() && !answer.customText().isBlank()) {
                throw new IllegalArgumentException("This question does not allow custom text");
            }

            Set<String> validLabels = question.options().stream().map(option -> option.label()).collect(java.util.stream.Collectors.toSet());
            for (String selectedLabel : answer.selectedOptionLabels()) {
                if (!validLabels.contains(selectedLabel)) {
                    throw new IllegalArgumentException("Unexpected selected option: " + selectedLabel);
                }
            }
        }

        if (answeredIds.size() != questionSet.questions().size()) {
            throw new IllegalArgumentException("All questions in the interactive set must be answered");
        }
    }

    public record QuestionRouting(UUID clientId, UUID conversationId, UUID turnId) {
    }

    public record PendingQuestionView(QuestionRouting routing, StudentQuestionSet questionSet, Instant createdAt) {
    }

    public record CompletedQuestionInteraction(QuestionRouting routing,
                                               StudentQuestionSet questionSet,
                                               StudentQuestionResponse response,
                                               Instant completedAt) {
    }

    private record InteractionKey(UUID clientId, UUID conversationId) {
    }

    private record PendingInteraction(QuestionRouting routing,
                                      StudentQuestionSet questionSet,
                                      Instant createdAt,
                                      CompletableFuture<StudentQuestionResponse> responseFuture) {
    }
}
