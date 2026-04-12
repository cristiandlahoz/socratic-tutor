package com.wornux.chat;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.MainLayout;
import reactor.core.Disposable;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@SpringComponent
@RouteScope
@RouteScopeOwner(MainLayout.class)
public class ChatUiController implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final String CONVERSATION_QUERY_PARAMETER = "c";
    static final String DRAFT_QUERY_PARAMETER = "draft";
    static final String DRAFT_QUERY_VALUE = "new";

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final ConversationTitleService conversationTitleService;
    private final BrowserClientService browserClientService;
    private final ChatUiState state;
    private final AtomicLong streamGeneration = new AtomicLong();
    private transient Disposable activeStream;

    public ChatUiController(ChatService chatService,
                            ConversationService conversationService,
                            ConversationTitleService conversationTitleService,
                            BrowserClientService browserClientService,
                            ChatUiState state) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.conversationTitleService = conversationTitleService;
        this.browserClientService = browserClientService;
        this.state = state;
    }

    public ChatUiState state() {
        return state;
    }

    RouteInitialization initializeFromRoute(String requestedConversationParam, boolean draftRequested) {
        abortActiveStream();
        state.responseInProgress().set(false);
        ensureClientId();

        if (draftRequested) {
            state.activeConversationId().set(null);
            state.replaceMessages(List.of());
            refreshConversationHistory();
            return RouteInitialization.noReroute();
        }

        var requestedConversationId = parseUuid(requestedConversationParam).orElse(null);
        var resolvedConversation = conversationService.resolveActiveConversation(state.clientId().peek(), requestedConversationId);

        state.activeConversationId().set(resolvedConversation.activeConversationId());
        state.replaceMessages(resolvedConversation.messages().stream().map(MessageVm::fromStored).toList());
        state.replaceConversationHistory(resolvedConversation.conversations());

        if (requestedConversationParam != null
                && (requestedConversationId == null
                || !Objects.equals(requestedConversationId, resolvedConversation.activeConversationId()))) {
            return new RouteInitialization(true, resolvedConversation.activeConversationId());
        }

        if (requestedConversationParam == null && state.activeConversationId().peek() != null) {
            synchronizeAddressBar(state.activeConversationId().peek());
        }

        return RouteInitialization.noReroute();
    }

    public boolean openConversation(UUID conversationId) {
        if (state.responseInProgress().peek() || conversationId.equals(state.activeConversationId().peek())) {
            return false;
        }
        UI.getCurrent().navigate(ChatView.class,
                QueryParameters.of(CONVERSATION_QUERY_PARAMETER, conversationId.toString()));
        return true;
    }

    public boolean startNewChat() {
        if (state.responseInProgress().peek()) {
            return false;
        }
        UI.getCurrent().navigate(ChatView.class,
                QueryParameters.of(DRAFT_QUERY_PARAMETER, DRAFT_QUERY_VALUE));
        return true;
    }

    public boolean submitPrompt(Runnable onResponseUpdated, Runnable onResponseFinished) {
        var prompt = state.composerText().peek();
        if (state.responseInProgress().peek() || prompt.isBlank()) {
            return false;
        }

        ensureClientId();
        var ensuredConversation = ensureConversation(prompt);
        var conversationId = ensuredConversation.id();
        var clientId = state.clientId().peek();
        var streamId = streamGeneration.incrementAndGet();
        var firstTokenReceived = new AtomicBoolean(false);
        var ui = UI.getCurrent();

        if (ensuredConversation.newlyCreated()) {
            conversationTitleService.generateTitle(prompt).subscribe(generatedTitle -> {
                conversationService.renameConversationIfTitleMatches(
                        clientId,
                        conversationId,
                        ensuredConversation.fallbackTitle(),
                        generatedTitle);
                runUiSideEffect(ui, this::refreshConversationHistory);
            });
        }

        state.responseInProgress().set(true);
        state.messages().insertLast(MessageVm.user(prompt, Instant.now()));
        state.composerText().set("");
        var responseMessage = state.messages().insertLast(MessageVm.assistantLoading(Instant.now()));

        activeStream = chatService.chatStream(prompt, clientId, conversationId).subscribe(
                token -> {
                    if (streamGeneration.get() != streamId) {
                        return;
                    }
                    if (firstTokenReceived.compareAndSet(false, true)) {
                        responseMessage.update(MessageVm::stopLoading);
                    }
                    responseMessage.update(message -> message.append(token));
                    runUiSideEffect(ui, onResponseUpdated);
                },
                _ -> {
                    if (streamGeneration.get() != streamId) {
                        return;
                    }
                    responseMessage.update(message -> message.fallback("Lo siento, ocurrió un problema al generar la respuesta. Intenta nuevamente."));
                    finishResponse(ui, onResponseFinished);
                },
                () -> {
                    if (streamGeneration.get() != streamId) {
                        return;
                    }
                    responseMessage.update(MessageVm::stopLoading);
                    finishResponse(ui, onResponseFinished);
                }
        );

        return true;
    }

    public void refreshConversationHistory() {
        if (state.clientId().peek() == null) {
            return;
        }
        state.replaceConversationHistory(conversationService.listConversations(state.clientId().peek()));
    }

    private void finishResponse(UI ui, Runnable onResponseFinished) {
        state.responseInProgress().set(false);
        refreshConversationHistory();
        activeStream = null;
        runUiSideEffect(ui, onResponseFinished);
    }

    private void runUiSideEffect(UI ui, Runnable callback) {
        if (ui != null) {
            ui.access(callback::run);
            return;
        }
        callback.run();
    }

    private void ensureClientId() {
        if (state.clientId().peek() == null) {
            state.clientId().set(browserClientService.resolveClientId());
        }
    }

    private EnsuredConversation ensureConversation(String prompt) {
        if (state.activeConversationId().peek() != null) {
            return new EnsuredConversation(state.activeConversationId().peek(), false, null);
        }

        var conversation = conversationService.createConversation(state.clientId().peek(), prompt);
        state.activeConversationId().set(conversation.id());
        synchronizeAddressBar(state.activeConversationId().peek());
        refreshConversationHistory();
        return new EnsuredConversation(state.activeConversationId().peek(), true, conversation.title());
    }

    private void synchronizeAddressBar(UUID conversationId) {
        UI.getCurrent().getPage().getHistory().replaceState(
                null,
                new Location("", QueryParameters.of(CONVERSATION_QUERY_PARAMETER, conversationId.toString()))
        );
    }

    private void abortActiveStream() {
        streamGeneration.incrementAndGet();
        if (activeStream != null) {
            activeStream.dispose();
            activeStream = null;
        }
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record RouteInitialization(boolean rerouteRequired, UUID rerouteConversationId) {

        public static RouteInitialization noReroute() {
            return new RouteInitialization(false, null);
        }

        public boolean rerouteToRoot() {
            return rerouteConversationId == null;
        }
    }

    private record EnsuredConversation(UUID id, boolean newlyCreated, String fallbackTitle) {
    }
}
