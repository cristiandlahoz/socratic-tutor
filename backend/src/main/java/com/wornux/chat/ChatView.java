package com.wornux.chat;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;
import com.wornux.MainLayout;
import org.springframework.ai.chat.messages.MessageType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Route(value = "", layout = MainLayout.class)
public class ChatView extends Composite<Div> implements BeforeEnterObserver {

    private static final String CONVERSATION_QUERY_PARAMETER = "c";
    private static final String DRAFT_QUERY_PARAMETER = "draft";
    private static final String DRAFT_QUERY_VALUE = "new";

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final BrowserClientService browserClientService;
    private final ChatViewState state;
    private final Div emptyState;
    private final CodeMessageList messageList;
    private final Div historyScroller;
    private final TextArea composerField;
    private final Button sendButton;
    private boolean drawerStateBound;

    public ChatView(ChatService chatService,
                    ConversationService conversationService,
                    BrowserClientService browserClientService) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.browserClientService = browserClientService;
        state = new ChatViewState();

        emptyState = createEmptyState();
        emptyState.bindVisible(state.emptyStateVisible());

        messageList = new CodeMessageList();
        messageList.setMarkdown(true);
        messageList.setWidthFull();
        messageList.addClassName("chat-message-list");
        Signal.effect(messageList, () -> messageList.setItems(state.messages().get().stream()
                .map(messageSignal -> toMessageListItem(messageSignal.get()))
                .toList()));

        var conversationStack = new Div(emptyState, messageList);
        conversationStack.addClassName("chat-conversation-stack");

        historyScroller = new Div(conversationStack);
        historyScroller.setSizeFull();
        historyScroller.addClassName("chat-history");

        var floatingDrawerToggle = new DrawerToggle();
        floatingDrawerToggle.addThemeVariants(ButtonVariant.TERTIARY);
        floatingDrawerToggle.addClassName("chat-floating-toggle");

        composerField = new TextArea();
        composerField.setWidthFull();
        composerField.setPlaceholder("Escribe tu mensaje aqui...");
        composerField.setAriaLabel("Escribe tu mensaje aqui");
        composerField.addClassName("chat-composer-field");
        composerField.bindValue(state.composerText(), state.composerText()::set);
        composerField.bindEnabled(state.composerEnabled());
        composerField.addKeyDownListener(Key.ENTER, _ -> submitPrompt());

        sendButton = new Button(new Icon(VaadinIcon.ARROW_UP));
        sendButton.addClassName("chat-send-button");
        sendButton.setAriaLabel("Enviar mensaje");
        sendButton.bindEnabled(state.sendEnabled());
        sendButton.addClickListener(_ -> submitPrompt());

        var root = getContent();
        root.setSizeFull();
        root.addClassName("chat-content-view");
        root.add(floatingDrawerToggle, historyScroller, createInputShell());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        ensureMainLayoutBindings();
        state.clientId().set(browserClientService.resolveClientId());

        var draftRequested = event.getLocation()
                .getQueryParameters()
                .getSingleParameter(DRAFT_QUERY_PARAMETER)
                .filter(DRAFT_QUERY_VALUE::equals)
                .isPresent();
        var requestedConversationParam = event.getLocation()
                .getQueryParameters()
                .getSingleParameter(CONVERSATION_QUERY_PARAMETER)
                .orElse(null);
        var requestedConversationId = parseUuid(requestedConversationParam).orElse(null);

        if (draftRequested) {
            state.activeConversationId().set(null);
            state.replaceMessages(List.of());
            refreshConversationHistory();
            historyScroller.getElement().executeJs("this.scrollTop = 0;");
            return;
        }

        var resolvedConversation = conversationService.resolveActiveConversation(state.clientId().peek(), requestedConversationId);

        if (requestedConversationParam != null
                && (requestedConversationId == null
                || !Objects.equals(requestedConversationId, resolvedConversation.activeConversationId()))) {
            rerouteToResolvedConversation(event, resolvedConversation.activeConversationId());
            return;
        }

        state.activeConversationId().set(resolvedConversation.activeConversationId());
        state.replaceMessages(resolvedConversation.messages().stream().map(MessageVm::fromStored).toList());
        state.replaceConversationHistory(resolvedConversation.conversations());
        historyScroller.getElement().executeJs("this.scrollTop = 0;");

        if (requestedConversationParam == null && state.activeConversationId().peek() != null) {
            synchronizeAddressBar(state.activeConversationId().peek());
        }
    }

    private Div createEmptyState() {
        var state = new Div();
        state.addClassName("chat-empty-state");

        var animation = new LottiePlayer("/animations/chat-welcome.json", true, true);
        animation.addClassName("chat-empty-animation");
        animation.setSpeed(0.6);

        var animationFrame = new Div(animation);
        animationFrame.addClassName("chat-empty-animation-frame");

        var eyebrow = new Html("<div class='chat-sidebar-badge'>Asistente academico</div>");

        var title = new H2("Haz tu primera pregunta");
        title.addClassName("chat-empty-title");

        var description = new Paragraph("Escribe y te ayudare a razonar paso a paso, aclarar conceptos y practicar con ejemplos.");
        description.addClassName("chat-empty-copy");

        state.add(animationFrame, eyebrow, title, description);
        return state;
    }

    private Div createInputShell() {
        var composer = new Div(composerField, sendButton);
        composer.addClassName("chat-composer");

        var inputShell = new Div(composer);
        inputShell.addClassName("chat-input-shell");
        return inputShell;
    }

    private void submitPrompt() {
        var prompt = state.composerText().peek();
        if (state.responseInProgress().peek() || prompt.isBlank()) {
            return;
        }

        if (state.clientId().peek() == null) {
            state.clientId().set(browserClientService.resolveClientId());
        }

        var conversationId = ensureConversation(prompt);
        state.responseInProgress().set(true);

        state.messages().insertLast(MessageVm.user(prompt, Instant.now()));
        state.composerText().set("");

        var responseMessage = state.messages().insertLast(MessageVm.assistantLoading(Instant.now()));
        scrollConversationToBottom();

        var uiOptional = getUI();
        var firstTokenReceived = new AtomicBoolean(false);

        uiOptional.ifPresent(ui -> chatService.chatStream(prompt, conversationId)
                .subscribe(token -> ui.access(() -> {
                            if (firstTokenReceived.compareAndSet(false, true)) {
                                responseMessage.update(MessageVm::stopLoading);
                            }
                            responseMessage.update(message -> message.append(token));
                            scrollConversationToBottom();
                        }),
                        _ -> ui.access(() -> {
                            responseMessage.update(message -> message.fallback("Lo siento, ocurrio un problema al generar la respuesta. Intenta nuevamente."));
                            state.responseInProgress().set(false);
                            refreshConversationHistory();
                            scrollConversationToBottom();
                        }),
                        () -> ui.access(() -> {
                            responseMessage.update(MessageVm::stopLoading);
                            state.responseInProgress().set(false);
                            refreshConversationHistory();
                            scrollConversationToBottom();
                        })));

        if (uiOptional.isEmpty()) {
            state.responseInProgress().set(false);
        }
    }

    private UUID ensureConversation(String prompt) {
        if (state.activeConversationId().peek() != null) {
            return state.activeConversationId().peek();
        }

        var conversation = conversationService.createConversation(state.clientId().peek(), prompt);
        state.activeConversationId().set(conversation.id());
        synchronizeAddressBar(state.activeConversationId().peek());
        refreshConversationHistory();
        return state.activeConversationId().peek();
    }

    private CodeMessageListItem toMessageListItem(MessageVm message) {
        var isUserMessage = message.role() == MessageType.USER;
        var item = new CodeMessageListItem(
                message.content(),
                message.createdAt(),
                isUserMessage ? "You" : "Socratic Tutor"
        );
        item.setUserColorIndex(isUserMessage ? 0 : 1);
        item.addClassNames(isUserMessage ? "user-message" : "tutor-message");
        if (message.loading()) {
            item.addClassNames("tutor-loading");
        }
        return item;
    }

    private void refreshConversationHistory() {
        if (state.clientId().peek() == null) {
            return;
        }
        state.replaceConversationHistory(conversationService.listConversations(state.clientId().peek()));
    }

    private void openConversation(UUID conversationId) {
        if (state.responseInProgress().peek() || conversationId.equals(state.activeConversationId().peek())) {
            return;
        }
        getUI().ifPresent(ui -> ui.navigate(ChatView.class, QueryParameters.of(CONVERSATION_QUERY_PARAMETER, conversationId.toString())));
    }

    private void startNewChat() {
        if (state.responseInProgress().peek()) {
            return;
        }
        getUI().ifPresent(ui -> ui.navigate(ChatView.class, QueryParameters.of(DRAFT_QUERY_PARAMETER, DRAFT_QUERY_VALUE)));
    }

    private void rerouteToResolvedConversation(BeforeEnterEvent event, UUID resolvedConversationId) {
        if (resolvedConversationId == null) {
            event.rerouteTo(ChatView.class, QueryParameters.empty());
            return;
        }
        event.rerouteTo(ChatView.class, QueryParameters.of(CONVERSATION_QUERY_PARAMETER, resolvedConversationId.toString()));
    }

    private void synchronizeAddressBar(UUID conversationId) {
        getUI().ifPresent(ui -> ui.getPage()
                .getHistory()
                .replaceState(null, new Location("", QueryParameters.of(CONVERSATION_QUERY_PARAMETER, conversationId.toString()))));
    }

    private void withMainLayout(java.util.function.Consumer<MainLayout> consumer) {
        getUI().flatMap(ui -> ui.getActiveRouterTargetsChain().stream()
                .filter(MainLayout.class::isInstance)
                .map(MainLayout.class::cast)
                .findFirst()).ifPresent(consumer);
    }

    private void ensureMainLayoutBindings() {
        if (drawerStateBound) {
            return;
        }
        withMainLayout(layout -> {
            layout.setConversationActions(this::startNewChat, this::openConversation);
            layout.bindConversationState(
                    state.conversationHistory(),
                    state.activeConversationId().asReadonly(),
                    state.responseInProgress().asReadonly()
            );
            drawerStateBound = true;
        });
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

    private void scrollConversationToBottom() {
        historyScroller.getElement().executeJs("this.scrollTo({ top: this.scrollHeight, behavior: 'smooth' });");
    }
}
