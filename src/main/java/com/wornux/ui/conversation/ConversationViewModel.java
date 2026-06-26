package com.wornux.ui.conversation;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.data.enums.ThemePreference;
import com.wornux.dtos.chat.StudentQuestionExchange;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.services.chat.ChatService;
import com.wornux.services.chat.ChatUsageService;
import com.wornux.services.chat.ConversationService;
import com.wornux.services.chat.ConversationTitleService;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.services.ui.ThemePreferenceService;
import com.wornux.ui.MainLayout;

@SpringComponent
@RouteScope
@RouteScopeOwner(MainLayout.class)
public class ConversationViewModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final String CONVERSATION_QUERY_PARAMETER = "c";
    static final String DRAFT_QUERY_PARAMETER = "draft";
    static final String DRAFT_QUERY_VALUE = "new";

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final ChatUsageService chatUsageService;
    private final ConversationTitleService conversationTitleService;
    private final ThemePreferenceService themePreferenceService;
    private final ActiveAcademicContextResolver contextResolver;
    private final ConversationNavigationOrchestrator navigationOrchestrator;
    private final ConversationThemeOrchestrator themeOrchestrator;
    private final ConversationTurnOrchestrator turnOrchestrator;
    private final ConversationState state;
    private final StudentQuestionExchange questionExchange;

    public ConversationViewModel(
            ChatService chatService,
            ConversationService conversationService,
            ChatUsageService chatUsageService,
            ConversationTitleService conversationTitleService,
            ThemePreferenceService themePreferenceService,
            ActiveAcademicContextResolver contextResolver,
            ConversationNavigationOrchestrator navigationOrchestrator,
            ConversationThemeOrchestrator themeOrchestrator,
            ConversationTurnOrchestrator turnOrchestrator,
            @RouteScopeOwner(MainLayout.class) ConversationState state) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.chatUsageService = chatUsageService;
        this.conversationTitleService = conversationTitleService;
        this.themePreferenceService = themePreferenceService;
        this.contextResolver = contextResolver;
        this.navigationOrchestrator = navigationOrchestrator;
        this.themeOrchestrator = themeOrchestrator;
        this.turnOrchestrator = turnOrchestrator;
        this.state = state;
        this.questionExchange = new StudentQuestionExchange(state);
    }

    public ConversationState state() {
        return state;
    }

    public void initializeShellState() {
        ensureThemePreferenceLoaded();
        themeOrchestrator.applyThemePreference(state.themePreference().peek());
        refreshConversationHistory();
    }

    public void onThemePreferenceChanged(ThemePreference preference) {
        var resolvedPreference = themePreferenceService.updateThemePreference(preference);
        state.themePreference().set(resolvedPreference);
        state.themePreferenceLoaded().set(true);
        themeOrchestrator.applyThemePreference(resolvedPreference);
    }

    RouteInitialization initializeFromRoute(String requestedConversationParam, boolean draftRequested) {
        turnOrchestrator.abortActiveStream(questionExchange);
        state.responseInProgress().set(false);
        ensureThemePreferenceLoaded();
        themeOrchestrator.applyThemePreference(state.themePreference().peek());
        state.setupRequired().set(contextResolver.resolveCurrent().isEmpty());

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
        var resolvedConversation = conversationService.resolveActiveConversation(requestedConversationId);

        state.activeConversationId().set(resolvedConversation.activeConversationId());
        state.replaceMessages(resolvedConversation.messages().stream().map(MessageState::fromConversation).toList());
        state.replaceConversationHistory(resolvedConversation.conversations());
        refreshConversationTokenUsage();
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
                || state.questionSubmissionInProgress().peek()
                || conversationId.equals(state.activeConversationId().peek())) {
            return;
        }
        navigationOrchestrator.openConversation(CONVERSATION_QUERY_PARAMETER, conversationId);
    }

    public void onStartNewConversation() {
        if (state.responseInProgress().peek() || state.questionSubmissionInProgress().peek()) {
            return;
        }
        navigationOrchestrator.openDraft(DRAFT_QUERY_PARAMETER, DRAFT_QUERY_VALUE);
    }

    public boolean onSubmitPrompt(Runnable onResponseUpdated, Runnable onResponseFinished) {
        var prompt = state.composerText().peek();
        if (state.responseInProgress().peek() || state.pendingQuestionSet().peek() != null || prompt.isBlank()) {
            return false;
        }

        EnsuredConversation ensuredConversation;
        try {
            ensuredConversation = ensureConversation(prompt);
            state.setupRequired().set(false);
        }
        catch (SetupRequiredException exception) {
            state.setupRequired().set(true);
            state.setupMessage().set(exception.getMessage());
            return false;
        }
        turnOrchestrator.startTurn(
            new ConversationTurnOrchestrator.TurnContext(UUID.randomUUID(),
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
            this::refreshConversationTokenUsage,
            this::refreshCompactionStatus);
        return true;
    }

    public void refreshConversationHistory() {
        state.replaceConversationHistory(conversationService.listConversations());
    }

    public void refreshConversationTokenUsage() {
        var conversationId = state.activeConversationId().peek();
        if (conversationId == null) {
            state.clearUsage();
            return;
        }
        var usage = chatUsageService.getConversationTokenUsage(conversationId);
        state.usageInputTokens().set(usage.inputTokens());
        state.usagePercent().set(usage.usagePercent());
    }

    public void refreshCompactionStatus() {
        var conversationId = state.activeConversationId().peek();
        if (conversationId == null) {
            state.conversationCompacted().set(false);
            return;
        }
        state.conversationCompacted().set(conversationService.isConversationCompacted(conversationId));
    }

    public void onSubmitInteractiveQuestionResponse(StudentQuestionResponse response) {
        var pendingQuestionSet = state.pendingQuestionSet().peek();
        if (pendingQuestionSet == null) {
            return;
        }
        questionExchange.submit(response);
    }

    private void ensureThemePreferenceLoaded() {
        if (Boolean.TRUE.equals(state.themePreferenceLoaded().peek())) {
            return;
        }

        state.themePreference().set(themePreferenceService.getThemePreference());
        state.themePreferenceLoaded().set(true);
    }

    private EnsuredConversation ensureConversation(String prompt) {
        if (state.activeConversationId().peek() != null) {
            return new EnsuredConversation(state.activeConversationId().peek(), false, null);
        }

        var conversation = conversationService.createConversation(prompt);
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
