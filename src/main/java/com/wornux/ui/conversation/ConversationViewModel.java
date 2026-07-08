package com.wornux.ui.conversation;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.data.enums.ThemePreference;
import com.wornux.dtos.chat.StudentQuestionExchange;
import com.wornux.dtos.chat.questions.StudentQuestionResponse;
import com.wornux.services.chat.ChatService;
import com.wornux.services.chat.ChatSessionActivity;
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

    static final String THREAD_ID_PREFIX = "T-";

    private final transient ChatService chatService;
    private final transient ConversationService conversationService;
    private final transient ChatUsageService chatUsageService;
    private final transient ConversationTitleService conversationTitleService;
    private final transient ThemePreferenceService themePreferenceService;
    private final transient ActiveAcademicContextResolver contextResolver;
    private final transient ConversationNavigationOrchestrator navigationOrchestrator;
    private final transient ConversationThemeOrchestrator themeOrchestrator;
    private final transient ConversationTurnOrchestrator turnOrchestrator;
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
            @RouteScopeOwner(MainLayout.class) ConversationTurnOrchestrator turnOrchestrator,
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

    public void bindTurnUiAnchor(Component uiAnchor) {
        turnOrchestrator.bindUiAnchor(uiAnchor);
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

    RouteInitialization initializeFromRoute(String requestedThreadId, boolean refreshEvent) {
        ensureThemePreferenceLoaded();
        themeOrchestrator.applyThemePreference(state.themePreference().peek());
        state.setupRequired().set(contextResolver.resolveCurrent().isEmpty());

        if (refreshEvent && state.responseInProgress().peek()) {
            return RouteInitialization.noReroute();
        }

        turnOrchestrator.abortActiveStream(questionExchange);
        state.responseInProgress().set(false);
        state.activity().set(ChatSessionActivity.IDLE);

        if (requestedThreadId == null) {
            startNewConversationDraft();
            return RouteInitialization.noReroute();
        }

        var requestedConversationId = parsePublicThreadId(requestedThreadId).orElse(null);
        var resolvedConversation = conversationService.resolveActiveConversation(requestedConversationId);

        state.activeConversationId().set(resolvedConversation.activeConversationId());
        state.replaceMessages(resolvedConversation.messages().stream().map(MessageState::fromConversation).toList());
        state.replaceConversationHistory(resolvedConversation.conversations());
        refreshConversationTokenUsage();
        refreshCompactionStatus();

        if (requestedConversationId == null
                || !Objects.equals(requestedConversationId, resolvedConversation.activeConversationId())
                || !requestedThreadId.equals(toPublicThreadId(requestedConversationId))) {
            return new RouteInitialization(true, resolvedConversation.activeConversationId());
        }

        return RouteInitialization.noReroute();
    }

    public void onOpenConversation(UUID conversationId) {
        if (state.responseInProgress().peek()
                || state.questionSubmissionInProgress().peek()
                || conversationId.equals(state.activeConversationId().peek())) {
            return;
        }
        navigationOrchestrator.openConversation(conversationId);
    }

    public void onStartNewConversation() {
        if (state.responseInProgress().peek() || state.questionSubmissionInProgress().peek()) {
            return;
        }
        navigationOrchestrator.openNewConversation();
    }

    public boolean onSubmitPrompt() {
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

    private void startNewConversationDraft() {
        state.activeConversationId().set(null);
        state.replaceMessages(List.of());
        state.clearUsage();
        state.clearCompactionStatus();
        state.clearPendingQuestionState();
        refreshConversationHistory();
    }

    private EnsuredConversation ensureConversation(String prompt) {
        if (state.activeConversationId().peek() != null) {
            return new EnsuredConversation(state.activeConversationId().peek(), false, null);
        }

        var conversation = conversationService.createConversation(prompt);
        state.activeConversationId().set(conversation.id());
        state.clearUsage();
        state.clearCompactionStatus();
        navigationOrchestrator.synchronizeAddressBar(state.activeConversationId().peek());
        refreshConversationHistory();
        return new EnsuredConversation(state.activeConversationId().peek(), true, conversation.title());
    }

    static String toPublicThreadId(UUID conversationId) {
        return THREAD_ID_PREFIX + conversationId;
    }

    private static Optional<UUID> parsePublicThreadId(String value) {
        if (value == null || !value.startsWith(THREAD_ID_PREFIX)) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(value.substring(THREAD_ID_PREFIX.length())));
        }
        catch (IllegalArgumentException _) {
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
