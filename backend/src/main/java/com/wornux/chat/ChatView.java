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
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;
import com.wornux.MainLayout;
import org.springframework.ai.chat.messages.MessageType;

import java.util.UUID;

@Route(value = "", layout = MainLayout.class)
public class ChatView extends Composite<Div> implements BeforeEnterObserver {

    private final ChatUiController controller;
    private final CodeMessageList messageList;
    private final Div historyScroller;
    private final TextArea composerField;
    private final Button sendButton;

    public ChatView(ChatUiState state, ChatUiController controller, ChatProperties chatProperties) {
        this.controller = controller;

        Div emptyState = createEmptyState();
        emptyState.bindVisible(state.emptyStateVisible());

        messageList = new CodeMessageList();
        messageList.setMarkdown(true);
        messageList.setThinkingSpinner(chatProperties.getUi().getThinkingSpinner());
        messageList.setWidthFull();
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
        composerField.setPlaceholder("Escribe tu mensaje aquí...");
        composerField.setAriaLabel("Escribe tu mensaje aquí");
        composerField.addClassName("chat-composer-field");
        composerField.bindValue(state.composerText(), state.composerText()::set);
        composerField.bindEnabled(state.composerEnabled());
        composerField.setValueChangeMode(ValueChangeMode.EAGER);

        sendButton = new Button(new Icon(VaadinIcon.ARROW_UP));
        sendButton.addClassName("chat-send-button");
        sendButton.setAriaLabel("Enviar mensaje");
        sendButton.bindEnabled(state.sendEnabled());
        sendButton.addClickShortcut(Key.ENTER).listenOn(composerField);
        sendButton.addClickListener(_ -> submitPrompt());

        var root = getContent();
        root.setSizeFull();
        root.addClassName("chat-content-view");
        root.add(floatingDrawerToggle, historyScroller, createInputShell());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var draftRequested = event.getLocation()
                .getQueryParameters()
                .getSingleParameter(ChatUiController.DRAFT_QUERY_PARAMETER)
                .filter(ChatUiController.DRAFT_QUERY_VALUE::equals)
                .isPresent();
        var requestedConversationParam = event.getLocation()
                .getQueryParameters()
                .getSingleParameter(ChatUiController.CONVERSATION_QUERY_PARAMETER)
                .orElse(null);

        var initialization = controller.initializeFromRoute(requestedConversationParam, draftRequested);
        if (initialization.rerouteRequired()) {
            rerouteToResolvedConversation(event, initialization.rerouteConversationId());
            return;
        }
        historyScroller.getElement().executeJs("this.scrollTop = 0;");
    }

    private Div createEmptyState() {
        var state = new Div();
        state.addClassName("chat-empty-state");

        var animation = new LottiePlayer("/animations/chat-welcome.json", true, true);
        animation.addClassName("chat-empty-animation");
        animation.setSpeed(0.6);

        var animationFrame = new Div(animation);
        animationFrame.addClassName("chat-empty-animation-frame");

        var eyebrow = new Html("<div class='chat-sidebar-badge'>Asistente académico</div>");

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
        controller.submitPrompt(this::scrollConversationToBottom, this::scrollConversationToBottom);
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

    private void rerouteToResolvedConversation(BeforeEnterEvent event, UUID resolvedConversationId) {
        if (resolvedConversationId == null) {
            event.rerouteTo(ChatView.class, QueryParameters.empty());
            return;
        }
        event.rerouteTo(ChatView.class, QueryParameters.of(ChatUiController.CONVERSATION_QUERY_PARAMETER, resolvedConversationId.toString()));
    }

    private void scrollConversationToBottom() {
        historyScroller.getElement().executeJs("this.scrollTo({ top: this.scrollHeight, behavior: 'smooth' });");
    }
}
