package com.wornux.ui.chat;

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
import com.wornux.ui.MainLayout;

@SpringComponent
@RouteScope
@RouteScopeOwner(MainLayout.class)
public class ChatState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ValueSignal<UUID> clientId = new ValueSignal<>(null);
    private final ValueSignal<UUID> activeConversationId = new ValueSignal<>(null);
    private final ValueSignal<Boolean> responseInProgress = new ValueSignal<>(false);
    private final ValueSignal<Boolean> compactionInProgress = new ValueSignal<>(false);
    private final ValueSignal<String> compactionLabel = new ValueSignal<>("");
    private final ValueSignal<String> composerText = new ValueSignal<>("");
    private final ValueSignal<StudentQuestionSet> pendingQuestionSet = new ValueSignal<>(null);
    private final ValueSignal<Boolean> questionSubmissionInProgress = new ValueSignal<>(false);
    private final ValueSignal<Integer> usageInputTokens = new ValueSignal<>(null);
    private final ValueSignal<Integer> usagePercent = new ValueSignal<>(null);
    private final ValueSignal<Boolean> conversationCompacted = new ValueSignal<>(false);
    private final ValueSignal<Integer> compactionLevel = new ValueSignal<>(null);
    private final ValueSignal<Long> compactedFromTranscriptId = new ValueSignal<>(null);
    private final ValueSignal<Boolean> setupRequired = new ValueSignal<>(false);
    private final ValueSignal<String> setupMessage = new ValueSignal<>(
            "Academic setup is required before persisted tutor features can be used.");
    private final ValueSignal<ThemePreference> themePreference = new ValueSignal<>(ThemePreference.SYSTEM);
    private final ValueSignal<Boolean> themePreferenceLoaded = new ValueSignal<>(false);
    private final ListSignal<MessageState> messages = new ListSignal<>();
    private final ListSignal<ConversationSummary> conversationHistory = new ListSignal<>();
    private final Signal<Boolean> emptyStateVisible = Signal.computed(() -> messages.get().isEmpty());
    private final Signal<Boolean> questionPanelVisible = Signal.computed(() -> pendingQuestionSet.get() != null);
    private final Signal<Boolean> composerEnabled = Signal.computed(
        () -> !responseInProgress.get()
                && !compactionInProgress.get()
                && pendingQuestionSet.get() == null
                && !questionSubmissionInProgress.get());
    private final Signal<Boolean> sendEnabled = Signal.computed(
        () -> !responseInProgress.get()
                && !compactionInProgress.get()
                && pendingQuestionSet.get() == null
                && !questionSubmissionInProgress.get()
                && !composerText.get().isBlank());

    public ValueSignal<UUID> clientId() {
        return clientId;
    }

    public ValueSignal<UUID> activeConversationId() {
        return activeConversationId;
    }

    public ValueSignal<Boolean> responseInProgress() {
        return responseInProgress;
    }

    public ValueSignal<Boolean> compactionInProgress() {
        return compactionInProgress;
    }

    public ValueSignal<String> compactionLabel() {
        return compactionLabel;
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

    public ValueSignal<Integer> compactionLevel() {
        return compactionLevel;
    }

    public ValueSignal<Long> compactedFromTranscriptId() {
        return compactedFromTranscriptId;
    }

    public ValueSignal<Boolean> setupRequired() {
        return setupRequired;
    }

    public ValueSignal<String> setupMessage() {
        return setupMessage;
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
        compactionInProgress.set(false);
        compactionLabel.set("");
        conversationCompacted.set(false);
        compactionLevel.set(null);
        compactedFromTranscriptId.set(null);
    }

    public void clearPendingQuestionState() {
        pendingQuestionSet.set(null);
        questionSubmissionInProgress.set(false);
    }
}
