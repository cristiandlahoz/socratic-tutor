package com.wornux.presentation.chat;

import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.application.chat.ChatService;
import com.wornux.application.chat.ChatUsageService;
import com.wornux.application.chat.ConversationService;
import com.wornux.application.chat.ConversationTitleService;
import com.wornux.application.profile.StudentProfileService;
import com.wornux.domain.chat.StudentQuestionExchange;
import com.wornux.domain.chat.questions.StudentQuestionResponse;
import com.wornux.domain.profile.ThemePreference;
import com.wornux.infrastructure.web.BrowserClientService;
import com.wornux.presentation.MainLayout;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@SpringComponent
@RouteScope
@RouteScopeOwner(MainLayout.class)
public class ChatViewModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final String CONVERSATION_QUERY_PARAMETER = "c";
    static final String DRAFT_QUERY_PARAMETER = "draft";
    static final String DRAFT_QUERY_VALUE = "new";

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final ChatUsageService chatUsageService;
    private final ConversationTitleService conversationTitleService;
    private final BrowserClientService browserClientService;
    private final StudentProfileService studentProfileService;
    private final ChatNavigationOrchestrator navigationOrchestrator;
    private final ChatThemeOrchestrator themeOrchestrator;
    private final ChatTurnOrchestrator turnOrchestrator;
    private final ChatUiState state;
    private final StudentQuestionExchange questionExchange;

    public ChatViewModel(
            ChatService chatService,
            ConversationService conversationService,
            ChatUsageService chatUsageService,
            ConversationTitleService conversationTitleService,
            BrowserClientService browserClientService,
            StudentProfileService studentProfileService,
            ChatNavigationOrchestrator navigationOrchestrator,
            ChatThemeOrchestrator themeOrchestrator,
            ChatTurnOrchestrator turnOrchestrator,
            ChatUiState state) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.chatUsageService = chatUsageService;
        this.conversationTitleService = conversationTitleService;
        this.browserClientService = browserClientService;
        this.studentProfileService = studentProfileService;
        this.navigationOrchestrator = navigationOrchestrator;
        this.themeOrchestrator = themeOrchestrator;
        this.turnOrchestrator = turnOrchestrator;
        this.state = state;
        this.questionExchange = new StudentQuestionExchange(state);
    }

    public ChatUiState state() {
        return state;
    }

    public void initializeShellState() {
        ensureClientId();
        ensureThemePreferenceLoaded();
        themeOrchestrator.applyThemePreference(state.themePreference().peek());
    }

    public void onThemePreferenceChanged(ThemePreference preference) {
        ensureClientId();
        var resolvedPreference = studentProfileService.updateThemePreference(state.clientId().peek(), preference);
        state.themePreference().set(resolvedPreference);
        state.themePreferenceLoaded().set(true);
        themeOrchestrator.applyThemePreference(resolvedPreference);
    }

    RouteInitialization initializeFromRoute(String requestedConversationParam, boolean draftRequested) {
        turnOrchestrator.abortActiveStream(questionExchange);
        state.responseInProgress().set(false);
        state.compactionInProgress().set(false);
        ensureClientId();
        ensureThemePreferenceLoaded();
        themeOrchestrator.applyThemePreference(state.themePreference().peek());

        if (draftRequested) {
            state.activeConversationId().set(null);
            state.replaceMessages(List.of());
            state.clearUsage();
            state.clearCompactionStatus();
            state.clearPendingQuestionState();
            refreshConversationHistory();
            return RouteInitialization.noReroute();
        }

        var requestedConversationId = parseUuid(requestedConversationParam).orElse(null);
        var resolvedConversation =
                conversationService.resolveActiveConversation(state.clientId().peek(), requestedConversationId);

        state.activeConversationId().set(resolvedConversation.activeConversationId());
        state.replaceMessages(resolvedConversation.messages().stream().map(MessageUiState::fromStored).toList());
        state.replaceConversationHistory(resolvedConversation.conversations());
        refreshTranscriptUsage();
        refreshCompactionStatus();

        if (requestedConversationParam != null
                && (requestedConversationId == null
                        || !Objects.equals(requestedConversationId, resolvedConversation.activeConversationId()))) {
            return new RouteInitialization(true, resolvedConversation.activeConversationId());
        }

        if (requestedConversationParam == null && state.activeConversationId().peek() != null) {
            navigationOrchestrator
                    .synchronizeAddressBar(CONVERSATION_QUERY_PARAMETER, state.activeConversationId().peek());
        }

        return RouteInitialization.noReroute();
    }

    public void onOpenConversation(UUID conversationId) {
        if (state.responseInProgress().peek()
                || state.compactionInProgress().peek()
                || state.questionSubmissionInProgress().peek()
                || conversationId.equals(state.activeConversationId().peek())) {
            return;
        }
        navigationOrchestrator.openConversation(CONVERSATION_QUERY_PARAMETER, conversationId);
    }

    public void onStartNewChat() {
        if (state.responseInProgress().peek()
                || state.compactionInProgress().peek()
                || state.questionSubmissionInProgress().peek()) {
            return;
        }
        navigationOrchestrator.openDraft(DRAFT_QUERY_PARAMETER, DRAFT_QUERY_VALUE);
    }

    public boolean onSubmitPrompt(Runnable onResponseUpdated, Runnable onResponseFinished) {
        var prompt = state.composerText().peek();
        if (state.responseInProgress().peek() || state.pendingQuestionSet().peek() != null || prompt.isBlank()) {
            return false;
        }

        ensureClientId();
        var ensuredConversation = ensureConversation(prompt);
        turnOrchestrator.startTurn(
            new ChatTurnOrchestrator.TurnContext(UUID.randomUUID(),
                    state.clientId().peek(),
                    ensuredConversation.id(),
                    prompt,
                    ensuredConversation.newlyCreated(),
                    ensuredConversation.fallbackTitle(),
                    state),
            chatService,
            conversationService,
            conversationTitleService,
            questionExchange,
            onResponseUpdated,
            onResponseFinished,
            this::refreshConversationHistory,
            this::refreshTranscriptUsage,
            this::refreshCompactionStatus);
        return true;
    }

    public void refreshConversationHistory() {
        if (state.clientId().peek() == null) {
            return;
        }
        state.replaceConversationHistory(conversationService.listConversations(state.clientId().peek()));
    }

    public void refreshTranscriptUsage() {
        var clientId = state.clientId().peek();
        var conversationId = state.activeConversationId().peek();
        if (clientId == null || conversationId == null) {
            state.clearUsage();
            return;
        }
        var usage = chatUsageService.getActiveTranscriptUsage(clientId, conversationId);
        state.usageInputTokens().set(usage.inputTokens());
        state.usagePercent().set(usage.usagePercent());
    }

    public void refreshCompactionStatus() {
        var clientId = state.clientId().peek();
        var conversationId = state.activeConversationId().peek();
        if (clientId == null || conversationId == null) {
            state.conversationCompacted().set(false);
            state.compactionLevel().set(null);
            state.compactedFromTranscriptId().set(null);
            return;
        }
        var status = conversationService.getCompactionStatus(clientId, conversationId);
        state.conversationCompacted().set(status.compacted());
        state.compactionLevel().set(status.level());
        state.compactedFromTranscriptId().set(status.compactedFromTranscriptId());
    }

    public void onSubmitInteractiveQuestionResponse(StudentQuestionResponse response) {
        var pendingQuestionSet = state.pendingQuestionSet().peek();
        if (pendingQuestionSet == null) {
            return;
        }
        questionExchange.submit(response);
    }

    private void ensureClientId() {
        if (state.clientId().peek() == null) {
            state.clientId().set(browserClientService.resolveClientId());
        }
    }

    private void ensureThemePreferenceLoaded() {
        if (Boolean.TRUE.equals(state.themePreferenceLoaded().peek())) {
            return;
        }

        state.themePreference().set(studentProfileService.getThemePreference(state.clientId().peek()));
        state.themePreferenceLoaded().set(true);
    }

    private EnsuredConversation ensureConversation(String prompt) {
        if (state.activeConversationId().peek() != null) {
            return new EnsuredConversation(state.activeConversationId().peek(), false, null);
        }

        var conversation = conversationService.createConversation(state.clientId().peek(), prompt);
        state.activeConversationId().set(conversation.id());
        state.clearUsage();
        state.clearCompactionStatus();
        navigationOrchestrator.synchronizeAddressBar(CONVERSATION_QUERY_PARAMETER, state.activeConversationId().peek());
        refreshConversationHistory();
        return new EnsuredConversation(state.activeConversationId().peek(), true, conversation.title());
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(value));
        }
        catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record RouteInitialization(boolean rerouteRequired, UUID rerouteConversationId) {

        public static RouteInitialization noReroute() {
            return new RouteInitialization(false, null);
        }
    }

    private record EnsuredConversation(UUID id, boolean newlyCreated, String fallbackTitle) {}
}
