package com.wornux.ui.conversation;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ListSignal;
import com.vaadin.flow.signals.local.ValueSignal;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.data.enums.ThemePreference;
import com.wornux.dtos.chat.*;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import com.wornux.services.chat.ChatSessionActivity;
import com.wornux.services.chat.ModelAvailabilityStatus;
import com.wornux.ui.MainLayout;

@SpringComponent
@RouteScope
@RouteScopeOwner(MainLayout.class)
public class ConversationState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ValueSignal<UUID> activeConversationId = new ValueSignal<>(null);
    private final ValueSignal<Boolean> responseInProgress = new ValueSignal<>(false);
    private final ValueSignal<ChatSessionActivity> activity = new ValueSignal<>(ChatSessionActivity.IDLE);
    private final ValueSignal<String> composerText = new ValueSignal<>("");
    private final ValueSignal<StudentQuestionSet> pendingQuestionSet = new ValueSignal<>(null);
    private final ValueSignal<Boolean> questionSubmissionInProgress = new ValueSignal<>(false);
    private final ValueSignal<Integer> usageInputTokens = new ValueSignal<>(null);
    private final ValueSignal<Integer> usagePercent = new ValueSignal<>(null);
    private final ValueSignal<Boolean> conversationCompacted = new ValueSignal<>(false);
    private final ValueSignal<Boolean> setupRequired = new ValueSignal<>(false);
    private final ValueSignal<ModelAvailabilityStatus> modelAvailabilityStatus =
            new ValueSignal<>(ModelAvailabilityStatus.CHECKING);
    private final ValueSignal<String> setupMessage =
            new ValueSignal<>("Academic setup is required before persisted tutor features can be used.");
    private final ValueSignal<ThemePreference> themePreference = new ValueSignal<>(ThemePreference.SYSTEM);
    private final ValueSignal<Boolean> themePreferenceLoaded = new ValueSignal<>(false);
    private final ListSignal<MessageState> messages = new ListSignal<>();
    private final ListSignal<ConversationSummary> conversationHistory = new ListSignal<>();
    private final Signal<Boolean> emptyStateVisible = Signal.computed(() -> messages.get().isEmpty());
    private final Signal<Boolean> questionPanelVisible = Signal.computed(() -> pendingQuestionSet.get() != null);
    private final Signal<Boolean> composerEnabled = Signal.computed(
        () -> !responseInProgress.get() && pendingQuestionSet.get() == null && !questionSubmissionInProgress.get());
    private final Signal<Boolean> composerSubmitAllowed = Signal.computed(
        () -> !responseInProgress.get()
                && pendingQuestionSet.get() == null
                && !questionSubmissionInProgress.get()
                && modelAvailabilityStatus.get() == ModelAvailabilityStatus.CONNECTED);
    private final Signal<Boolean> sendEnabled = Signal.computed(
        () -> Boolean.TRUE.equals(composerSubmitAllowed.get()) && !composerText.get().isBlank());

    public ValueSignal<UUID> activeConversationId() {
        return activeConversationId;
    }

    public ValueSignal<Boolean> responseInProgress() {
        return responseInProgress;
    }

    public ValueSignal<ChatSessionActivity> activity() {
        return activity;
    }

    public ValueSignal<String> composerText() {
        return composerText;
    }

    public ValueSignal<StudentQuestionSet> pendingQuestionSet() {
        return pendingQuestionSet;
    }

    public ValueSignal<Boolean> questionSubmissionInProgress() {
        return questionSubmissionInProgress;
    }

    public ValueSignal<Integer> usageInputTokens() {
        return usageInputTokens;
    }

    public ValueSignal<Integer> usagePercent() {
        return usagePercent;
    }

    public ValueSignal<Boolean> conversationCompacted() {
        return conversationCompacted;
    }

    public ValueSignal<Boolean> setupRequired() {
        return setupRequired;
    }

    public ValueSignal<String> setupMessage() {
        return setupMessage;
    }

    public ValueSignal<ModelAvailabilityStatus> modelAvailabilityStatus() {
        return modelAvailabilityStatus;
    }

    public ValueSignal<ThemePreference> themePreference() {
        return themePreference;
    }

    public ValueSignal<Boolean> themePreferenceLoaded() {
        return themePreferenceLoaded;
    }

    public ListSignal<MessageState> messages() {
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

    public Signal<Boolean> questionPanelVisible() {
        return questionPanelVisible;
    }

    public Signal<Boolean> composerSubmitAllowed() {
        return composerSubmitAllowed;
    }

    public Signal<Boolean> sendEnabled() {
        return sendEnabled;
    }

    public void replaceMessages(List<MessageState> nextMessages) {
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

    public void clearCompactionStatus() {
        conversationCompacted.set(false);
    }

    public void clearPendingQuestionState() {
        pendingQuestionSet.set(null);
        questionSubmissionInProgress.set(false);
    }
}
