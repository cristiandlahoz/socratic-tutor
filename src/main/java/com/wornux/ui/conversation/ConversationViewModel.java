package com.wornux.ui.conversation;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.data.enums.ThemePreference;
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
    private final transient ConversationTurnOrchestrator turnOrchestrator;
    private final ConversationState state;

    public ConversationViewModel(
            ChatService chatService,
            ConversationService conversationService,
            ChatUsageService chatUsageService,
            ConversationTitleService conversationTitleService,
            ThemePreferenceService themePreferenceService,
            ActiveAcademicContextResolver contextResolver,
            @RouteScopeOwner(MainLayout.class) ConversationTurnOrchestrator turnOrchestrator,
            @RouteScopeOwner(MainLayout.class) ConversationState state) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.chatUsageService = chatUsageService;
        this.conversationTitleService = conversationTitleService;
        this.themePreferenceService = themePreferenceService;
        this.contextResolver = contextResolver;
        this.turnOrchestrator = turnOrchestrator;
        this.state = state;
    }

    public ConversationState state() {
        return state;
    }

    public void bindTurnUiAnchor(Component uiAnchor) {
        turnOrchestrator.bindUiAnchor(uiAnchor);
    }

    public void detachTurnStream() {
        turnOrchestrator.detachActiveStream();
    }

    public void detachTurnStream(Component uiAnchor) {
        turnOrchestrator.detachActiveStream(uiAnchor);
    }

    public void initializeShellState() {
        ensureThemePreferenceLoaded();
        applyThemePreference(state.themePreference().peek());
        refreshConversationHistory();
    }

    public void onThemePreferenceChanged(ThemePreference preference) {
        var resolvedPreference = themePreferenceService.updateThemePreference(preference);
        state.themePreference().set(resolvedPreference);
        state.themePreferenceLoaded().set(true);
        applyThemePreference(resolvedPreference);
    }

    RouteInitialization initializeFromRoute(String requestedThreadId, boolean refreshEvent) {
        ensureThemePreferenceLoaded();
        applyThemePreference(state.themePreference().peek());
        state.setupRequired().set(contextResolver.resolveCurrent().isEmpty());

        if (refreshEvent && state.responseInProgress().peek()) {
            return RouteInitialization.noReroute();
        }

        turnOrchestrator.detachActiveStream();
        state.responseInProgress().set(false);
        state.activity().set(ChatSessionActivity.IDLE);
        state.clearPendingQuestionState();

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
        attachToActiveConversationStream();

        if (requestedConversationId == null
                || !Objects.equals(requestedConversationId, resolvedConversation.activeConversationId())
                || !requestedThreadId.equals(toPublicThreadId(requestedConversationId))) {
            return new RouteInitialization(true, resolvedConversation.activeConversationId());
        }

        return RouteInitialization.noReroute();
    }

    public void onOpenConversation(UUID conversationId) {
        UI.getCurrent().navigate(ConversationView.class,
            new RouteParameters(ConversationView.THREAD_ROUTE_PARAMETER, toPublicThreadId(conversationId)));
    }

    public void onStartNewConversation() {
        UI.getCurrent().navigate(ConversationView.class);
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
        turnOrchestrator.submitInteractiveQuestionResponse(state.activeConversationId().peek(), response);
    }

    private void attachToActiveConversationStream() {
        turnOrchestrator.attachToConversation(
            state.activeConversationId().peek(),
            state,
            this::refreshConversationHistory,
            this::refreshConversationTokenUsage,
            this::refreshCompactionStatus);
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
        UI.getCurrent().getPage().getHistory().replaceState(null,
            new Location(ConversationView.ROUTE + "/" + toPublicThreadId(state.activeConversationId().peek())));
        refreshConversationHistory();
        return new EnsuredConversation(state.activeConversationId().peek(), true, conversation.title());
    }

    private static void applyThemePreference(ThemePreference preference) {
        var ui = UI.getCurrent();
        if (ui == null) {
            return;
        }

        var storageValue = (preference == null ? ThemePreference.SYSTEM : preference).storageValue();
        ui.getElement().setAttribute("data-theme-preference", storageValue);
        ui.getPage().executeJs("""
                               document.documentElement.setAttribute('data-theme-preference', $0);
                               document.body?.setAttribute('data-theme-preference', $0);
                               """, storageValue);
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
