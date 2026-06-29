package com.wornux.ui.conversation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.signals.local.ValueSignal;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.dtos.chat.StudentQuestionExchange;
import com.wornux.services.chat.ChatService;
import com.wornux.services.chat.ChatStreamTimeoutException;
import com.wornux.services.chat.ConversationService;
import com.wornux.services.chat.ConversationTitleService;
import com.wornux.ui.MainLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@SpringComponent
@RouteScope
@RouteScopeOwner(MainLayout.class)
public class ConversationTurnOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ConversationTurnOrchestrator.class);
    private final AtomicLong streamGeneration = new AtomicLong();
    private transient Disposable activeStream;
    private transient Component uiAnchor;
    private transient VaadinSession vaadinSession;

    public void bindUiAnchor(Component uiAnchor) {
        this.uiAnchor = uiAnchor;
        uiAnchor.getUI().ifPresent(ui -> vaadinSession = ui.getSession());
    }

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
            Runnable refreshConversationHistory,
            Runnable refreshConversationTokenUsage,
            Runnable refreshCompactionStatus) {

        var streamId = streamGeneration.incrementAndGet();
        var firstTokenReceived = new AtomicBoolean(false);
        vaadinSession = VaadinSession.getCurrent();
        if (context.newConversation()) {
            conversationTitleService.generateTitle(context.prompt()).subscribe(generatedTitle -> runUiSideEffect(() -> {
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
                .subscribe(token -> runUiSideEffect(() -> {
                    if (streamGeneration.get() != streamId) {
                        return;
                    }
                    if (firstTokenReceived.compareAndSet(false, true)) {
                        responseMessage.update(messageVm -> Objects.requireNonNull(messageVm).stopLoading());
                    }
                    responseMessage.update(message -> Objects.requireNonNull(message).append(token));
                }), exception -> {
                    if (streamGeneration.get() != streamId) {
                        return;
                    }
                    logStreamFailure(context, exception);
                    if (exception instanceof ChatStreamTimeoutException) {
                        handleOpenAiStreamTimeout(context, userMessage, responseMessage);
                    }
                    else {
                        runUiSideEffect(() -> responseMessage.update(
                            message -> Objects.requireNonNull(message)
                                    .fallback(
                                        "Lo siento, ocurrió un problema al generar la respuesta. Intenta nuevamente.")));
                    }
                    finishResponse(
                        context.state(),
                        refreshConversationHistory,
                        refreshConversationTokenUsage,
                        refreshCompactionStatus);
                }, () -> runUiSideEffect(() -> {
                    if (streamGeneration.get() != streamId) {
                        return;
                    }
                    responseMessage.update(messageVm -> Objects.requireNonNull(messageVm).stopLoading());
                    finalizeTurn(
                        context,
                        chatService,
                        refreshConversationHistory,
                        refreshConversationTokenUsage,
                        refreshCompactionStatus);
                }));
    }

    private void finalizeTurn(
            TurnContext context,
            ChatService chatService,
            Runnable refreshConversationHistory,
            Runnable refreshConversationTokenUsage,
            Runnable refreshCompactionStatus) {
        activeStream = Mono.fromRunnable(() -> chatService.finalizeTurn(context.turnId(), context.conversationId()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    _ -> {},
                    _ -> finishResponse(
                        context.state(),
                        refreshConversationHistory,
                        refreshConversationTokenUsage,
                        refreshCompactionStatus),
                    () -> finishResponse(
                        context.state(),
                        refreshConversationHistory,
                        refreshConversationTokenUsage,
                        refreshCompactionStatus));
    }

    private void finishResponse(
            ConversationState state,
            Runnable refreshConversationHistory,
            Runnable refreshConversationTokenUsage,
            Runnable refreshCompactionStatus) {
        runUiSideEffect(() -> {
            state.responseInProgress().set(false);
            refreshConversationHistory.run();
            refreshConversationTokenUsage.run();
            refreshCompactionStatus.run();
            activeStream = null;
        });
    }

    private void runUiSideEffect(Runnable callback) {
        if (uiAnchor != null && uiAnchor.getUI().isPresent()) {
            uiAnchor.getUI().get().access(callback::run);
            return;
        }
        if (vaadinSession != null) {
            vaadinSession.access(callback::run);
            return;
        }
        callback.run();
    }

    private void handleOpenAiStreamTimeout(
            TurnContext context,
            ValueSignal<MessageState> userMessage,
            ValueSignal<MessageState> responseMessage) {
        runUiSideEffect(() -> {
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
