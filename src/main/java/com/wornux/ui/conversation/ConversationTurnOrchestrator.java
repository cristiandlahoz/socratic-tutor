package com.wornux.ui.conversation;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.services.chat.ChatService;
import com.wornux.services.chat.ChatSessionActivity;
import com.wornux.services.chat.ConversationService;
import com.wornux.services.chat.ConversationTitleService;
import com.wornux.ui.MainLayout;

@SpringComponent
@RouteScope
@RouteScopeOwner(MainLayout.class)
public class ConversationTurnOrchestrator {

    private final ConversationTurnStreamRegistry streamRegistry;
    private Map<Component, StreamAttachment> streamAttachments = new ConcurrentHashMap<>();
    private Component uiAnchor;

    public ConversationTurnOrchestrator(ConversationTurnStreamRegistry streamRegistry) {
        this.streamRegistry = streamRegistry;
    }

    public void bindUiAnchor(Component uiAnchor) {
        this.uiAnchor = uiAnchor;
    }

    public void attachToConversation(
            UUID conversationId,
            ConversationState state,
            Runnable refreshConversationHistory,
            Runnable refreshConversationTokenUsage,
            Runnable refreshCompactionStatus) {
        attachToConversation(
            uiAnchor,
            conversationId,
            state,
            refreshConversationHistory,
            refreshConversationTokenUsage,
            refreshCompactionStatus);
    }

    public void attachToConversation(
            Component anchor,
            UUID conversationId,
            ConversationState state,
            Runnable refreshConversationHistory,
            Runnable refreshConversationTokenUsage,
            Runnable refreshCompactionStatus) {
        if (anchor == null) {
            return;
        }
        var existingAttachment = streamAttachments().get(anchor);
        if (existingAttachment != null && Objects.equals(existingAttachment.conversationId(), conversationId)) {
            return;
        }
        closeStreamAttachment(anchor);
        if (conversationId == null) {
            return;
        }
        var ui = anchor.getUI().orElse(null);
        var vaadinSession = ui == null ? VaadinSession.getCurrent() : ui.getSession();
        var subscription = streamRegistry.subscribe(
            conversationId,
            snapshot -> runUiSideEffect(anchor, ui, vaadinSession, () -> applySnapshot(
                snapshot,
                state,
                refreshConversationHistory,
                refreshConversationTokenUsage,
                refreshCompactionStatus)));
        streamAttachments().put(anchor, new StreamAttachment(conversationId, subscription));
    }

    public void detachActiveStream() {
        detachActiveStream(uiAnchor);
    }

    public void detachActiveStream(Component anchor) {
        closeStreamAttachment(anchor);
    }

    public void startTurn(
            TurnContext context,
            ChatService chatService,
            ConversationService conversationService,
            ConversationTitleService conversationTitleService,
            Runnable refreshConversationHistory,
            Runnable refreshConversationTokenUsage,
            Runnable refreshCompactionStatus) {
        var turnAnchor = uiAnchor;
        attachToConversation(
            turnAnchor,
            context.conversationId(),
            context.state(),
            refreshConversationHistory,
            refreshConversationTokenUsage,
            refreshCompactionStatus);
        if (streamRegistry.hasActiveTurn(context.conversationId())) {
            return;
        }
        context.state().composerText().set("");
        streamRegistry.startTurn(
            context,
            chatService,
            conversationService,
            conversationTitleService,
            () -> runUiSideEffect(turnAnchor, refreshConversationHistory));
    }

    public boolean submitInteractiveQuestionResponse(UUID conversationId, StudentQuestionResponse response) {
        if (conversationId == null) {
            return false;
        }
        return streamRegistry.submitQuestionResponse(conversationId, response);
    }

    private void applySnapshot(
            ConversationTurnStreamRegistry.TurnSnapshot snapshot,
            ConversationState state,
            Runnable refreshConversationHistory,
            Runnable refreshConversationTokenUsage,
            Runnable refreshCompactionStatus) {
        if (!Objects.equals(state.activeConversationId().peek(), snapshot.conversationId())) {
            return;
        }
        state.applyMessagesSnapshot(snapshot.messages());
        state.responseInProgress().set(snapshot.responseInProgress());
        state.activity().set(snapshot.activity());
        state.pendingQuestionSet().set(snapshot.pendingQuestionSet());
        state.questionSubmissionInProgress().set(snapshot.questionSubmissionInProgress());
        if (!snapshot.terminal()) {
            return;
        }
        if (snapshot.retryPrompt() != null) {
            state.composerText().set(snapshot.retryPrompt());
            showStreamTimeoutNotification();
        }
        refreshConversationHistory.run();
        refreshConversationTokenUsage.run();
        refreshCompactionStatus.run();
    }

    private void closeStreamAttachment(Component anchor) {
        if (anchor == null) {
            return;
        }
        var attachment = streamAttachments().remove(anchor);
        if (attachment == null) {
            return;
        }
        try {
            attachment.subscription().close();
        }
        catch (Exception _) {
            // Best-effort UI subscription cleanup.
        }
    }

    private Map<Component, StreamAttachment> streamAttachments() {
        if (streamAttachments == null) {
            streamAttachments = new ConcurrentHashMap<>();
        }
        return streamAttachments;
    }

    private void runUiSideEffect(Component anchor, Runnable callback) {
        var ui = anchor == null ? null : anchor.getUI().orElse(null);
        var vaadinSession = ui == null ? VaadinSession.getCurrent() : ui.getSession();
        runUiSideEffect(anchor, ui, vaadinSession, callback);
    }

    private void runUiSideEffect(Component anchor, UI ui, VaadinSession vaadinSession, Runnable callback) {
        if (anchor != null && anchor.getUI().isPresent()) {
            anchor.getUI().get().access(callback::run);
            return;
        }
        if (ui != null && ui.isAttached()) {
            ui.access(callback::run);
            return;
        }
        if (vaadinSession != null) {
            vaadinSession.access(callback::run);
            return;
        }
        callback.run();
    }

    private void showStreamTimeoutNotification() {
        var notification = Notification.show(
            "La respuesta tardó demasiado. Tu mensaje quedó listo para reintentar.",
            5_000,
            Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.ERROR);
        notification.addThemeName("terminal");
    }

    public record TurnContext(UUID turnId, UUID conversationId, String prompt, boolean newConversation,
            String fallbackTitle, ConversationState state) {}

    private record StreamAttachment(UUID conversationId, AutoCloseable subscription) {}
}
