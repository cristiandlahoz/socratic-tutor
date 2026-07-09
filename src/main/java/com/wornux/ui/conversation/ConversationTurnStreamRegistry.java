package com.wornux.ui.conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.vaadin.flow.signals.local.ValueSignal;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import com.wornux.services.chat.ChatService;
import com.wornux.services.chat.ChatSessionActivity;
import com.wornux.services.chat.ChatSessionActivityBus;
import com.wornux.services.chat.ChatStreamTimeoutException;
import com.wornux.services.chat.ConversationTitleService;
import com.wornux.services.chat.ConversationService;
import com.wornux.services.chat.ModelAvailabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
class ConversationTurnStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConversationTurnStreamRegistry.class);
    private static final long QUESTION_RESPONSE_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private final ChatSessionActivityBus activityBus;
    private final ModelAvailabilityService modelAvailabilityService;
    private final Map<UUID, ActiveTurn> activeTurns = new ConcurrentHashMap<>();
    private final Map<UUID, CopyOnWriteArrayList<Consumer<TurnSnapshot>>> subscribers = new ConcurrentHashMap<>();

    ConversationTurnStreamRegistry(
            ChatSessionActivityBus activityBus,
            ModelAvailabilityService modelAvailabilityService) {
        this.activityBus = activityBus;
        this.modelAvailabilityService = modelAvailabilityService;
    }

    AutoCloseable subscribe(UUID conversationId, Consumer<TurnSnapshot> listener) {
        subscribers.computeIfAbsent(conversationId, _ -> new CopyOnWriteArrayList<>()).add(listener);
        var activeTurn = activeTurns.get(conversationId);
        if (activeTurn != null) {
            publishToListener(conversationId, listener, activeTurn.snapshot(false, null));
        }
        return () -> unsubscribe(conversationId, listener);
    }

    boolean hasActiveTurn(UUID conversationId) {
        var activeTurn = activeTurns.get(conversationId);
        return activeTurn != null && activeTurn.responseInProgress;
    }

    boolean startTurn(
            ConversationTurnOrchestrator.TurnContext context,
            ChatService chatService,
            ConversationService conversationService,
            ConversationTitleService conversationTitleService,
            Runnable refreshConversationHistory) {
        var activeTurn = new ActiveTurn(context.turnId(), context.conversationId(), context.prompt());
        var existingTurn = activeTurns.putIfAbsent(context.conversationId(), activeTurn);
        if (existingTurn != null) {
            return false;
        }

        activeTurn.messages.addAll(context.state().messages().peek().stream().map(ValueSignal::peek).toList());
        generateTitleForNewConversation(context, conversationService, conversationTitleService, refreshConversationHistory);
        markTurnAsGenerating(activeTurn);

        var userMessage = MessageState.user(context.prompt(), Instant.now());
        var responseMessage = MessageState.assistantLoading(Instant.now());
        synchronized (activeTurn) {
            activeTurn.messages.add(userMessage);
            activeTurn.messages.add(responseMessage);
        }
        activeTurn.broadcast(false, null);

        var firstTokenReceived = new AtomicBoolean(false);
        activeTurn.stream = chatService
                .chatStream(
                    context.turnId(),
                    context.prompt(),
                    context.conversationId(),
                    activeTurn::ask,
                    sanitized -> replaceUserMessage(activeTurn, userMessage.createdAt(), sanitized))
                .subscribeOn(Schedulers.boundedElastic())
                .contextCapture()
                .subscribe(
                    token -> appendAssistantToken(activeTurn, firstTokenReceived, responseMessage.createdAt(), token),
                    exception -> handleStreamFailure(activeTurn, userMessage.createdAt(), responseMessage.createdAt(), exception),
                    () -> finishStreamedMessage(activeTurn, responseMessage.createdAt(), chatService));
        return true;
    }

    private void unsubscribe(UUID conversationId, Consumer<TurnSnapshot> listener) {
        var conversationSubscribers = subscribers.get(conversationId);
        if (conversationSubscribers == null) {
            return;
        }
        conversationSubscribers.remove(listener);
        if (conversationSubscribers.isEmpty()) {
            subscribers.remove(conversationId, conversationSubscribers);
        }
    }

    private void publishToListener(UUID conversationId, Consumer<TurnSnapshot> listener, TurnSnapshot snapshot) {
        try {
            listener.accept(snapshot);
        }
        catch (Throwable exception) {
            unsubscribe(conversationId, listener);
            log.debug(
                "chat_ui_stream_listener_removed conversation_id={} failure_type={} failure_message={}",
                conversationId,
                exception.getClass().getSimpleName(),
                exception.getMessage());
        }
    }

    boolean submitQuestionResponse(UUID conversationId, StudentQuestionResponse response) {
        var activeTurn = activeTurns.get(conversationId);
        if (activeTurn == null) {
            return false;
        }
        return activeTurn.submit(response);
    }

    private void generateTitleForNewConversation(
            ConversationTurnOrchestrator.TurnContext context,
            ConversationService conversationService,
            ConversationTitleService conversationTitleService,
            Runnable refreshConversationHistory) {
        if (!context.newConversation()) {
            return;
        }

        conversationTitleService.generateTitle(context.prompt()).subscribe(generatedTitle -> {
            conversationService.renameConversationIfTitleMatches(
                context.conversationId(),
                context.fallbackTitle(),
                generatedTitle);
            refreshConversationHistory.run();
        });
    }

    private void markTurnAsGenerating(ActiveTurn activeTurn) {
        activeTurn.responseInProgress = true;
        activeTurn.activity = ChatSessionActivity.GENERATING;
        activeTurn.activitySubscription = activityBus.subscribe(
            activeTurn.conversationId.toString(),
            activity -> {
                activeTurn.activity = activity;
                activeTurn.broadcast(false, null);
            });
    }

    private void appendAssistantToken(
            ActiveTurn activeTurn,
            AtomicBoolean firstTokenReceived,
            Instant responseCreatedAt,
            String token) {
        synchronized (activeTurn) {
            if (firstTokenReceived.compareAndSet(false, true)) {
                replaceMessage(activeTurn, responseCreatedAt, message -> message.stopLoading());
            }
            modelAvailabilityService.markConnected();
            replaceMessage(activeTurn, responseCreatedAt, message -> message.append(token));
        }
        activeTurn.broadcast(false, null);
    }

    private void handleStreamFailure(
            ActiveTurn activeTurn,
            Instant userCreatedAt,
            Instant responseCreatedAt,
            Throwable exception) {
        logStreamFailure(activeTurn, exception);
        modelAvailabilityService.markOffline();
        if (exception instanceof ChatStreamTimeoutException) {
            synchronized (activeTurn) {
                activeTurn.messages.removeIf(message -> message.createdAt().equals(userCreatedAt)
                        || message.createdAt().equals(responseCreatedAt));
            }
            finishTurn(activeTurn, true, activeTurn.prompt);
            return;
        }
        synchronized (activeTurn) {
            replaceMessage(
                activeTurn,
                responseCreatedAt,
                message -> message.fallback("Lo siento, ocurrió un problema al generar la respuesta. Intenta nuevamente."));
        }
        finishTurn(activeTurn, false, null);
    }

    private void finishStreamedMessage(ActiveTurn activeTurn, Instant responseCreatedAt, ChatService chatService) {
        synchronized (activeTurn) {
            replaceMessage(activeTurn, responseCreatedAt, MessageState::stopLoading);
        }
        modelAvailabilityService.markConnected();
        activeTurn.stream = Mono.fromRunnable(() -> chatService.finalizeTurn(activeTurn.turnId, activeTurn.conversationId))
                .subscribeOn(Schedulers.boundedElastic())
                .contextCapture()
                .subscribe(_ -> {}, _ -> finishTurn(activeTurn, false, null), () -> finishTurn(activeTurn, false, null));
    }

    private void finishTurn(ActiveTurn activeTurn, boolean timeout, String retryPrompt) {
        activeTurn.responseInProgress = false;
        activeTurn.activity = ChatSessionActivity.IDLE;
        activeTurn.closeActivitySubscription();
        activeTurn.clearPendingQuestionState();
        activeTurn.broadcast(true, timeout ? retryPrompt : null);
        activeTurns.remove(activeTurn.conversationId, activeTurn);
    }

    private void replaceMessage(ActiveTurn activeTurn, Instant createdAt, java.util.function.Function<MessageState, MessageState> update) {
        for (int index = 0; index < activeTurn.messages.size(); index++) {
            var message = activeTurn.messages.get(index);
            if (message.createdAt().equals(createdAt)) {
                activeTurn.messages.set(index, update.apply(message));
                return;
            }
        }
    }

    private void replaceUserMessage(ActiveTurn activeTurn, Instant createdAt, String sanitized) {
        synchronized (activeTurn) {
            replaceMessage(activeTurn, createdAt, message -> message.withSteeredContent(sanitized));
        }
        activeTurn.broadcast(false, null);
    }

    private void logStreamFailure(ActiveTurn activeTurn, Throwable exception) {
        if (exception instanceof ChatStreamTimeoutException) {
            log.warn(
                "chat_ui_stream_timeout turn_id={} conversation_id={} message={}",
                activeTurn.turnId,
                activeTurn.conversationId,
                exception.getMessage());
            return;
        }
        log.warn(
            "chat_ui_stream_failed turn_id={} conversation_id={} error_type={} error_message={}",
            activeTurn.turnId,
            activeTurn.conversationId,
            exception.getClass().getSimpleName(),
            exception.getMessage(),
            exception);
    }

    record TurnSnapshot(
            UUID conversationId,
            List<MessageState> messages,
            boolean responseInProgress,
            ChatSessionActivity activity,
            StudentQuestionSet pendingQuestionSet,
            boolean questionSubmissionInProgress,
            boolean terminal,
            String retryPrompt) {}

    private final class ActiveTurn {

        private final UUID turnId;
        private final UUID conversationId;
        private final String prompt;
        private final List<MessageState> messages = new ArrayList<>();
        private volatile boolean responseInProgress;
        private volatile ChatSessionActivity activity = ChatSessionActivity.IDLE;
        private volatile StudentQuestionSet pendingQuestionSet;
        private volatile boolean questionSubmissionInProgress;
        private volatile CompletableFuture<StudentQuestionResponse> pendingResponse;
        private volatile Disposable stream;
        private volatile AutoCloseable activitySubscription;

        private ActiveTurn(UUID turnId, UUID conversationId, String prompt) {
            this.turnId = turnId;
            this.conversationId = conversationId;
            this.prompt = prompt;
        }

        private StudentQuestionResponse ask(StudentQuestionSet questionSet) {
            CompletableFuture<StudentQuestionResponse> responseFuture;
            synchronized (this) {
                if (pendingResponse != null && !pendingResponse.isDone()) {
                    throw new IllegalStateException("There is already a pending interactive question flow");
                }
                responseFuture = new CompletableFuture<>();
                pendingResponse = responseFuture;
                questionSubmissionInProgress = false;
                pendingQuestionSet = questionSet;
            }
            broadcast(false, null);

            try {
                return responseFuture.get(QUESTION_RESPONSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
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
            catch (java.util.concurrent.ExecutionException exception) {
                throw new IllegalStateException("Failed while waiting for student response", exception.getCause());
            }
            finally {
                synchronized (this) {
                    if (pendingResponse == responseFuture) {
                        pendingResponse = null;
                    }
                }
                clearPendingQuestionState();
                broadcast(false, null);
            }
        }

        private boolean submit(StudentQuestionResponse response) {
            CompletableFuture<StudentQuestionResponse> responseFuture;
            synchronized (this) {
                responseFuture = pendingResponse;
                if (responseFuture == null || responseFuture.isDone()) {
                    return false;
                }
                questionSubmissionInProgress = true;
            }
            broadcast(false, null);
            if (!responseFuture.complete(response)) {
                questionSubmissionInProgress = false;
                broadcast(false, null);
                return false;
            }
            return true;
        }

        private void clearPendingQuestionState() {
            pendingQuestionSet = null;
            questionSubmissionInProgress = false;
        }

        private TurnSnapshot snapshot(boolean terminal, String retryPrompt) {
            synchronized (this) {
                return new TurnSnapshot(
                    conversationId,
                    List.copyOf(messages),
                    responseInProgress,
                    activity,
                    pendingQuestionSet,
                    questionSubmissionInProgress,
                    terminal,
                    retryPrompt);
            }
        }

        private void broadcast(boolean terminal, String retryPrompt) {
            var snapshot = snapshot(terminal, retryPrompt);
            var listeners = subscribers.getOrDefault(conversationId, new CopyOnWriteArrayList<>());
            listeners.forEach(listener -> publishToListener(conversationId, listener, snapshot));
        }

        private void closeActivitySubscription() {
            if (activitySubscription == null) {
                return;
            }
            try {
                activitySubscription.close();
            }
            catch (Exception _) {
                // Best-effort listener cleanup.
            }
            activitySubscription = null;
        }
    }
}
