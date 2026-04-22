package com.wornux.chat;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.wornux.MainLayout;
import com.wornux.chat.questions.StudentQuestion;
import com.wornux.chat.questions.StudentQuestionOption;
import com.wornux.chat.questions.StudentQuestionResponse;
import com.wornux.chat.questions.StudentQuestionSet;
import com.wornux.chat.tools.QuestionInteractionService;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

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
    private final ChatUsageService chatUsageService;
    private final ConversationTitleService conversationTitleService;
    private final BrowserClientService browserClientService;
    private final QuestionInteractionService questionInteractionService;
    private final ChatProperties chatProperties;
    private final ChatUiState state;
    private final AtomicLong streamGeneration = new AtomicLong();
    private transient Disposable activeStream;

    public ChatUiController(ChatService chatService,
                            ConversationService conversationService,
                            ChatUsageService chatUsageService,
                            ConversationTitleService conversationTitleService,
                            BrowserClientService browserClientService,
                            QuestionInteractionService questionInteractionService,
                            ChatProperties chatProperties,
                            ChatUiState state) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.chatUsageService = chatUsageService;
        this.conversationTitleService = conversationTitleService;
        this.browserClientService = browserClientService;
        this.questionInteractionService = questionInteractionService;
        this.chatProperties = chatProperties;
        this.state = state;
    }

    public ChatUiState state() {
        return state;
    }

    RouteInitialization initializeFromRoute(String requestedConversationParam, boolean draftRequested) {
        abortActiveStream();
        state.responseInProgress().set(false);
        state.compactionInProgress().set(false);
        ensureClientId();

        if (draftRequested) {
            state.activeConversationId().set(null);
            state.replaceMessages(List.of());
            state.clearUsage();
            state.clearCompactionStatus();
            showPreviewQuestionSetIfEnabled();
            refreshConversationHistory();
            return RouteInitialization.noReroute();
        }

        var requestedConversationId = parseUuid(requestedConversationParam).orElse(null);
        var resolvedConversation = conversationService.resolveActiveConversation(state.clientId().peek(), requestedConversationId);

        state.activeConversationId().set(resolvedConversation.activeConversationId());
        state.replaceMessages(resolvedConversation.messages().stream().map(MessageVm::fromStored).toList());
        state.replaceConversationHistory(resolvedConversation.conversations());
        refreshTranscriptUsage();
        refreshCompactionStatus();
        syncPendingQuestionState();

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
        if (state.responseInProgress().peek()
                || state.compactionInProgress().peek()
                || state.questionSubmissionInProgress().peek()
                || conversationId.equals(state.activeConversationId().peek())) {
            return false;
        }
        UI.getCurrent().navigate(ChatView.class,
                QueryParameters.of(CONVERSATION_QUERY_PARAMETER, conversationId.toString()));
        return true;
    }

    public boolean startNewChat() {
        if (state.responseInProgress().peek() || state.compactionInProgress().peek() || state.questionSubmissionInProgress().peek()) {
            return false;
        }
        UI.getCurrent().navigate(ChatView.class,
                QueryParameters.of(DRAFT_QUERY_PARAMETER, DRAFT_QUERY_VALUE));
        return true;
    }

    public boolean submitPrompt(Runnable onResponseUpdated, Runnable onResponseFinished) {
        var prompt = state.composerText().peek();
        if (state.responseInProgress().peek() || state.pendingQuestionSet().peek() != null || prompt.isBlank()) {
            return false;
        }

        ensureClientId();
        var ensuredConversation = ensureConversation(prompt);
        var conversationId = ensuredConversation.id();
        var clientId = state.clientId().peek();
        var turnId = UUID.randomUUID();
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

        activeStream = chatService.chatStream(turnId, prompt, clientId, conversationId).subscribe(
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
                    startCompactionPhase();
                    finalizeTurn(turnId, clientId, conversationId, prompt, responseMessage.peek().content(), ui, onResponseFinished);
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

    public void syncPendingQuestionState() {
        var clientId = state.clientId().peek();
        var conversationId = state.activeConversationId().peek();
        if (clientId == null) {
            state.clearPendingQuestionState();
            return;
        }
        if (conversationId == null) {
            showPreviewQuestionSetIfEnabled();
            return;
        }
        var pendingQuestionSet = questionInteractionService.findPending(clientId, conversationId)
                .map(QuestionInteractionService.PendingQuestionView::questionSet)
                .orElse(null);
        if (!Objects.equals(state.pendingQuestionSet().peek(), pendingQuestionSet)) {
            state.pendingQuestionSet().set(pendingQuestionSet);
        }
        if (pendingQuestionSet == null) {
            state.questionSubmissionInProgress().set(false);
        }
    }

    public boolean submitInteractiveQuestionResponse(StudentQuestionResponse response) {
        var clientId = state.clientId().peek();
        var conversationId = state.activeConversationId().peek();
        var pendingQuestionSet = state.pendingQuestionSet().peek();
        if (clientId == null || pendingQuestionSet == null) {
            return false;
        }
        if (conversationId == null && isPreviewStudentQuestionPanelEnabled() && Objects.equals(pendingQuestionSet, previewQuestionSetOrNull())) {
            state.clearPendingQuestionState();
            return true;
        }
        if (conversationId == null) {
            return false;
        }
        state.questionSubmissionInProgress().set(true);
        try {
            questionInteractionService.submitResponse(clientId, conversationId, response);
            state.clearPendingQuestionState();
            return true;
        } catch (RuntimeException exception) {
            state.questionSubmissionInProgress().set(false);
            throw exception;
        }
    }

    private void finishResponse(UI ui, Runnable onResponseFinished) {
        state.responseInProgress().set(false);
        state.compactionInProgress().set(false);
        state.compactionLabel().set("");
        refreshConversationHistory();
        refreshTranscriptUsage();
        refreshCompactionStatus();
        activeStream = null;
        runUiSideEffect(ui, onResponseFinished);
    }

    private void startCompactionPhase() {
        state.responseInProgress().set(false);
        state.compactionInProgress().set(true);
        state.compactionLabel().set("Compactando, no deberia tardar...");
    }

    private void finalizeTurn(UUID turnId,
                              UUID clientId,
                              UUID conversationId,
                              String userInput,
                              String assistantResponse,
                              UI ui,
                              Runnable onResponseFinished) {
        activeStream = Mono.fromCallable(() -> chatService.finalizeTurn(turnId, clientId, conversationId, userInput, assistantResponse))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        _ -> finishResponse(ui, onResponseFinished),
                        _ -> finishResponse(ui, onResponseFinished)
                );
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
        state.clearUsage();
        state.clearCompactionStatus();
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

    private void showPreviewQuestionSetIfEnabled() {
        var previewQuestionSet = previewQuestionSetOrNull();
        state.pendingQuestionSet().set(previewQuestionSet);
        state.questionSubmissionInProgress().set(false);
    }

    private StudentQuestionSet previewQuestionSetOrNull() {
        return isPreviewStudentQuestionPanelEnabled() ? buildPreviewQuestionSet() : null;
    }

    private boolean isPreviewStudentQuestionPanelEnabled() {
        var ui = chatProperties.getUi();
        return ui != null && ui.isPreviewStudentQuestionPanel();
    }

    private StudentQuestionSet buildPreviewQuestionSet() {
        return new StudentQuestionSet(
                "antes de seguir",
                "diagnosis",
                StudentQuestionSet.ProfileImpact.PEDAGOGICAL,
                List.of(
                        new StudentQuestion(
                                "current_block",
                                "bloqueo",
                                "¿qué te está frenando más ahora mismo?",
                                List.of(
                                        new StudentQuestionOption("Entender la idea", "Todavía no me queda claro el concepto base."),
                                        new StudentQuestionOption("Aplicarlo", "Entiendo la teoría, pero no sé usarla en un ejercicio."),
                                        new StudentQuestionOption("Empezar", "No sé cuál debería ser el primer paso."),
                                        new StudentQuestionOption("Verificar", "Ya lo intenté, pero no sé si va bien.")
                                ),
                                false
                        ),
                        new StudentQuestion(
                                "help_style",
                                "ayuda",
                                "¿cómo prefieres que te ayude?",
                                List.of(
                                        new StudentQuestionOption("Paso a paso", "Quiero avanzar en partes cortas y comprobables."),
                                        new StudentQuestionOption("Pista breve", "Prefiero una pista y luego intentar yo."),
                                        new StudentQuestionOption("Ejemplo", "Me ayuda más ver un caso resuelto parecido.")
                                ),
                                false
                        ),
                        new StudentQuestion(
                                "confidence_level",
                                "confianza",
                                "¿qué tan seguro te sientes con este tema?",
                                List.of(
                                        new StudentQuestionOption("Muy perdido", "Necesito volver a la base."),
                                        new StudentQuestionOption("Más o menos", "Tengo parte clara y parte confusa."),
                                        new StudentQuestionOption("Casi listo", "Solo necesito confirmar un detalle.")
                                ),
                                false
                        )
                )
        );
    }
}
