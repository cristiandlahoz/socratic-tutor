package com.wornux.chat;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.Route;
import org.springframework.ai.chat.messages.MessageType;
import com.wornux.MainLayout;

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
    private final Div emptyState;
    private final CodeMessageList messageList;
    private final Div historyScroller;
    private final TextArea composerField;
    private final Button sendButton;
    private UUID clientId;
    private UUID activeConversationId;
    private boolean responseInProgress;

    public ChatView(ChatService chatService,
                    ConversationService conversationService,
                    BrowserClientService browserClientService) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.browserClientService = browserClientService;

        emptyState = createEmptyState();
        messageList = new CodeMessageList();
        messageList.setMarkdown(true);
        messageList.setWidthFull();
        messageList.addClassName("chat-message-list");
        messageList.setItems(List.of());

        var conversationStack = new Div(emptyState, messageList);
        conversationStack.addClassName("chat-conversation-stack");

        historyScroller = new Div(conversationStack);
        historyScroller.setSizeFull();
        historyScroller.addClassName("chat-history");

        DrawerToggle floatingDrawerToggle = new DrawerToggle();
        floatingDrawerToggle.addThemeVariants(ButtonVariant.AURA_TERTIARY);
        floatingDrawerToggle.addClassName("chat-floating-toggle");

        composerField = new TextArea();
        composerField.setWidthFull();
        composerField.setPlaceholder("Escribe tu mensaje aqui...");
        composerField.setAriaLabel("Escribe tu mensaje aqui");
        composerField.addClassName("chat-composer-field");

        sendButton = new Button(new Icon(VaadinIcon.ARROW_UP));
        sendButton.addClassName("chat-send-button");
        sendButton.setAriaLabel("Enviar mensaje");
        sendButton.addClickListener(_ -> submitPrompt());

        var root = getContent();
        root.setSizeFull();
        root.addClassName("chat-content-view");
        root.add(floatingDrawerToggle, historyScroller, createInputShell());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        clientId = browserClientService.resolveClientId();
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
            activeConversationId = null;
            renderConversation(List.of());
            updateDrawer(conversationService.listConversations(clientId));
            return;
        }

        var resolvedConversation = conversationService.resolveActiveConversation(clientId, requestedConversationId);

        if (requestedConversationParam != null
                && (requestedConversationId == null
                || !Objects.equals(requestedConversationId, resolvedConversation.activeConversationId()))) {
            rerouteToResolvedConversation(event, resolvedConversation.activeConversationId());
            return;
        }

        activeConversationId = resolvedConversation.activeConversationId();
        renderConversation(resolvedConversation.messages());
        updateDrawer(resolvedConversation.conversations());

        if (requestedConversationParam == null && activeConversationId != null) {
            synchronizeAddressBar(activeConversationId);
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
        var prompt = composerField.getValue();
        if (responseInProgress || prompt.isBlank()) {
            return;
        }

        if (clientId == null) {
            clientId = browserClientService.resolveClientId();
        }

        var conversationId = ensureConversation(prompt);
        emptyState.setVisible(false);
        setLoadingState(true);

        var promptMessage = new CodeMessageListItem(prompt, Instant.now(), "You");
        promptMessage.setUserColorIndex(0);
        promptMessage.addClassNames("user-message");
        messageList.addItem(promptMessage);
        composerField.clear();

        var responseMessage = new CodeMessageListItem("", Instant.now(), "Socratic Tutor");
        responseMessage.setUserColorIndex(1);
        responseMessage.addClassNames("tutor-message", "tutor-loading");
        messageList.addItem(responseMessage);
        scrollConversationToBottom();

        var uiOptional = getUI();
        var firstTokenReceived = new AtomicBoolean(false);

        uiOptional.ifPresent(ui -> chatService.chatStream(prompt, conversationId)
                .subscribe(token -> ui.access(() -> {
                            if (firstTokenReceived.compareAndSet(false, true)) {
                                responseMessage.removeClassNames("tutor-loading");
                            }
                            responseMessage.appendText(token);
                            scrollConversationToBottom();
                        }),
                        _ -> ui.access(() -> {
                            responseMessage.removeClassNames("tutor-loading");
                            if (responseMessage.getText().isBlank()) {
                                responseMessage.setText("Lo siento, ocurrio un problema al generar la respuesta. Intenta nuevamente.");
                            }
                            setLoadingState(false);
                            refreshConversationHistory();
                            scrollConversationToBottom();
                        }),
                        () -> ui.access(() -> {
                            responseMessage.removeClassNames("tutor-loading");
                            setLoadingState(false);
                            refreshConversationHistory();
                            scrollConversationToBottom();
                        })));

        if (uiOptional.isEmpty()) {
            setLoadingState(false);
        }
    }

    private UUID ensureConversation(String prompt) {
        if (activeConversationId != null) {
            return activeConversationId;
        }

        var conversation = conversationService.createConversation(clientId, prompt);
        activeConversationId = conversation.id();
        synchronizeAddressBar(activeConversationId);
        refreshConversationHistory();
        return activeConversationId;
    }

    private void renderConversation(List<StoredChatMessage> messages) {
        var items = messages.stream()
                .map(this::toMessageListItem)
                .toList();
        messageList.setItems(items);
        emptyState.setVisible(items.isEmpty());
        historyScroller.getElement().executeJs("this.scrollTop = 0;");
    }

    private CodeMessageListItem toMessageListItem(StoredChatMessage storedChatMessage) {
        var isUserMessage = storedChatMessage.role() == MessageType.USER;
        var item = new CodeMessageListItem(
                storedChatMessage.content(),
                storedChatMessage.createdAt(),
                isUserMessage ? "You" : "Socratic Tutor"
        );
        item.setUserColorIndex(isUserMessage ? 0 : 1);
        item.addClassNames(isUserMessage ? "user-message" : "tutor-message");
        return item;
    }

    private void refreshConversationHistory() {
        if (clientId == null) {
            return;
        }
        updateDrawer(conversationService.listConversations(clientId));
    }

    private void updateDrawer(List<ConversationSummary> conversations) {
        withMainLayout(layout -> {
            layout.setConversationActions(this::startNewChat, this::openConversation);
            layout.updateConversationHistory(conversations, activeConversationId, responseInProgress);
        });
    }

    private void openConversation(UUID conversationId) {
        if (responseInProgress || conversationId.equals(activeConversationId)) {
            return;
        }
        getUI().ifPresent(ui -> ui.navigate(ChatView.class, QueryParameters.of(CONVERSATION_QUERY_PARAMETER, conversationId.toString())));
    }

    private void startNewChat() {
        if (responseInProgress) {
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
        getUI().ifPresent(ui -> ui.getActiveRouterTargetsChain().stream()
                .filter(MainLayout.class::isInstance)
                .map(MainLayout.class::cast)
                .findFirst()
                .ifPresent(consumer));
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

    private void setLoadingState(boolean loading) {
        responseInProgress = loading;
        composerField.setEnabled(!loading);
        sendButton.setEnabled(!loading);
        refreshConversationHistory();
    }
}
