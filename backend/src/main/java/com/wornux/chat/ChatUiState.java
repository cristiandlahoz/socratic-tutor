package com.wornux.chat;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ListSignal;
import com.vaadin.flow.signals.local.ValueSignal;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.MainLayout;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@SpringComponent
@RouteScope
@RouteScopeOwner(MainLayout.class)
public class ChatUiState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ValueSignal<UUID> clientId = new ValueSignal<>(null);
    private final ValueSignal<UUID> activeConversationId = new ValueSignal<>(null);
    private final ValueSignal<Boolean> responseInProgress = new ValueSignal<>(false);
    private final ValueSignal<String> composerText = new ValueSignal<>("");
    private final ValueSignal<Integer> usageInputTokens = new ValueSignal<>(null);
    private final ValueSignal<Integer> usagePercent = new ValueSignal<>(null);
    private final ListSignal<MessageVm> messages = new ListSignal<>();
    private final ListSignal<ConversationSummary> conversationHistory = new ListSignal<>();
    private final Signal<Boolean> emptyStateVisible = Signal.computed(() -> messages.get().isEmpty());
    private final Signal<Boolean> composerEnabled = Signal.not(responseInProgress);
    private final Signal<Boolean> sendEnabled = Signal.computed(() -> !responseInProgress.get() && !composerText.get().isBlank());

    public ValueSignal<UUID> clientId() {
        return clientId;
    }

    public ValueSignal<UUID> activeConversationId() {
        return activeConversationId;
    }

    public ValueSignal<Boolean> responseInProgress() {
        return responseInProgress;
    }

    public ValueSignal<String> composerText() {
        return composerText;
    }

    public ValueSignal<Integer> usageInputTokens() {
        return usageInputTokens;
    }

    public ValueSignal<Integer> usagePercent() {
        return usagePercent;
    }

    public ListSignal<MessageVm> messages() {
        return messages;
    }

    public ListSignal<ConversationSummary> conversationHistory() {
        return conversationHistory;
    }

    public Signal<Boolean> emptyStateVisible() {
        return emptyStateVisible;
    }

    public Signal<Boolean> composerEnabled() {
        return composerEnabled;
    }

    public Signal<Boolean> sendEnabled() {
        return sendEnabled;
    }

    public void replaceMessages(List<MessageVm> nextMessages) {
        messages.clear();
        nextMessages.forEach(messages::insertLast);
    }

    public void replaceConversationHistory(List<ConversationSummary> conversations) {
        conversationHistory.clear();
        conversations.forEach(conversationHistory::insertLast);
    }

    public void clearUsage() {
        usageInputTokens.set(null);
        usagePercent.set(null);
    }
}
