package com.wornux.ui.conversation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.signals.local.ValueSignal;
import com.wornux.dtos.chat.StudentQuestionExchange;
import com.wornux.services.chat.ChatService;
import com.wornux.services.chat.ChatStreamTimeoutException;
import com.wornux.services.chat.ConversationService;
import com.wornux.services.chat.ConversationTitleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class ConversationTurnOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ConversationTurnOrchestrator.class);
    private final AtomicLong streamGeneration = new AtomicLong();
    private transient Disposable activeStream;

    public void abortActiveStream(StudentQuestionExchange questionExchange) {
        streamGeneration.incrementAndGet();
        if (activeStream != null) {
            activeStream.dispose();
            activeStream = null;
        }
        questionExchange.cancelPending();
    }

    public void startTurn(
            TurnContext context,
            ChatService chatService,
            ConversationService conversationService,
            ConversationTitleService conversationTitleService,
            StudentQuestionExchange questionExchange,
            Runnable onResponseUpdated,
            Runnable onResponseFinished,
            Runnable refreshConversationHistory,
            Runnable refreshConversationTokenUsage,
            Runnable refreshCompactionStatus) {

        var streamId = streamGeneration.incrementAndGet();
        var firstTokenReceived = new AtomicBoolean(false);
        var ui = UI.getCurrent();

        if (context.newConversation()) {
            conversationTitleService.generateTitle(context.prompt()).subscribe(generatedTitle -> runUiSideEffect(ui, () -> {
                conversationService.renameConversationIfTitleMatches(
                    context.conversationId(),
                    context.fallbackTitle(),
                    generatedTitle);
                refreshConversationHistory.run();
            }));
        }

        context.state().responseInProgress().set(true);
        var userMessage = context.state().messages().insertLast(MessageState.user(context.prompt(), Instant.now()));
        context.state().composerText().set("");
        var responseMessage = context.state().messages().insertLast(MessageState.assistantLoading(Instant.now()));

        activeStream = chatService
                .chatStream(context.turnId(), context.prompt(), context.conversationId(), questionExchange::ask)
                .subscribe(token -> {
                    if (streamGeneration.get() != streamId) {
                        return;
                    }
                    if (firstTokenReceived.compareAndSet(false, true)) {
                        responseMessage.update(messageVm -> Objects.requireNonNull(messageVm).stopLoading());
                    }
                    responseMessage.update(message -> Objects.requireNonNull(message).append(token));
                    runUiSideEffect(ui, onResponseUpdated);
                }, exception -> {
                    if (streamGeneration.get() != streamId) {
                        return;
                    }
                    logStreamFailure(context, exception);
                    if (exception instanceof ChatStreamTimeoutException) {
                        handleOpenAiStreamTimeout(context, userMessage, responseMessage, ui);
                    }
                    else {
                        responseMessage.update(
                            message -> Objects.requireNonNull(message)
                                    .fallback(
                                        "Lo siento, ocurrió un problema al generar la respuesta. Intenta nuevamente."));
                    }
                    finishResponse(
                        context.state(),
                        ui,
                        onResponseFinished,
                        refreshConversationHistory,
                        refreshConversationTokenUsage,
                        refreshCompactionStatus);
                }, () -> {
                    if (streamGeneration.get() != streamId) {
                        return;
                    }
                    responseMessage.update(messageVm -> Objects.requireNonNull(messageVm).stopLoading());
                    finalizeTurn(
                        context,
                        chatService,
                        onResponseFinished,
                        refreshConversationHistory,
                        refreshConversationTokenUsage,
                        refreshCompactionStatus,
                        ui);
                });
    }

    private void finalizeTurn(
            TurnContext context,
            ChatService chatService,
            Runnable onResponseFinished,
            Runnable refreshConversationHistory,
            Runnable refreshConversationTokenUsage,
            Runnable refreshCompactionStatus,
            UI ui) {
        activeStream = Mono.fromRunnable(() -> chatService.finalizeTurn(context.turnId(), context.conversationId()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    _ -> {},
                    _ -> finishResponse(
                        context.state(),
                        ui,
                        onResponseFinished,
                        refreshConversationHistory,
                        refreshConversationTokenUsage,
                        refreshCompactionStatus),
                    () -> finishResponse(
                        context.state(),
                        ui,
                        onResponseFinished,
                        refreshConversationHistory,
                        refreshConversationTokenUsage,
                        refreshCompactionStatus));
    }

    private void finishResponse(
            ConversationState state,
            UI ui,
            Runnable onResponseFinished,
            Runnable refreshConversationHistory,
            Runnable refreshConversationTokenUsage,
            Runnable refreshCompactionStatus) {
        runUiSideEffect(ui, () -> {
            state.responseInProgress().set(false);
            refreshConversationHistory.run();
            refreshConversationTokenUsage.run();
            refreshCompactionStatus.run();
            activeStream = null;
            onResponseFinished.run();
        });
    }

    private void runUiSideEffect(UI ui, Runnable callback) {
        if (ui != null) {
            ui.access(callback::run);
            return;
        }
        callback.run();
    }

    private void handleOpenAiStreamTimeout(
            TurnContext context,
            ValueSignal<MessageState> userMessage,
            ValueSignal<MessageState> responseMessage,
            UI ui) {
        runUiSideEffect(ui, () -> {
            context.state().messages().remove(responseMessage);
            context.state().messages().remove(userMessage);
            context.state().composerText().set(context.prompt());
            showStreamTimeoutNotification();
        });
    }

    private void showStreamTimeoutNotification() {
        var notification = Notification.show(
            "La respuesta tardó demasiado. Tu mensaje quedó listo para reintentar.",
            5_000,
            Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.ERROR);
        notification.addThemeName("terminal");
    }

    private void logStreamFailure(TurnContext context, Throwable exception) {
        if (exception instanceof ChatStreamTimeoutException) {
            log.warn(
                "chat_ui_stream_timeout turn_id={} conversation_id={} message={}",
                context.turnId(),
                context.conversationId(),
                exception.getMessage());
            return;
        }
        log.warn(
            """
            chat_ui_stream_failed turn_id={} conversation_id={} failure_kind={}\
             error_type={} error_message={}\
            """,
            context.turnId(),
            context.conversationId(),
            chatFailureKind(exception),
            exception.getClass().getSimpleName(),
            exception.getMessage(),
            exception);
    }

    private String chatFailureKind(Throwable exception) {
        for (Throwable cursor = exception; cursor != null; cursor = cursor.getCause()) {
            if (cursor.getMessage() != null && cursor.getMessage().contains("Timed out waiting for student response")) {
                return "interactive_question_timeout";
            }
        }
        return "chat_stream_error";
    }

    public record TurnContext(UUID turnId, UUID conversationId, String prompt, boolean newConversation,
            String fallbackTitle, ConversationState state) {}
}
