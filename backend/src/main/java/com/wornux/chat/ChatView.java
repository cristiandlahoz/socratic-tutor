package com.wornux.chat;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import com.wornux.MainLayout;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Route(value = "", layout = MainLayout.class)
public class ChatView extends Composite<Div> {

    private final ChatService chatService;
    private final Div emptyState;
    private final MessageList messageList;
    private final TextArea composerField;
    private final Button sendButton;
    private final String chatId = UUID.randomUUID().toString();
    private boolean responseInProgress;

    public ChatView(ChatService chatService) {
        this.chatService = chatService;

        emptyState = createEmptyState();
        messageList = new MessageList();
        messageList.setMarkdown(true);
        messageList.setWidthFull();
        messageList.addClassName("chat-message-list");

        var conversationStack = new Div(emptyState, messageList);
        conversationStack.addClassName("chat-conversation-stack");

        var historyScroller = new Div(conversationStack);
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

        emptyState.setVisible(false);
        setLoadingState(true);

        var promptMessage = new MessageListItem(prompt, Instant.now(), "You");
        promptMessage.setUserColorIndex(0);
        promptMessage.addClassNames("user-message");
        messageList.addItem(promptMessage);
        composerField.clear();

        var responseMessage = new MessageListItem("", Instant.now(), "Socratic Tutor");
        responseMessage.setUserColorIndex(1);
        responseMessage.addClassNames("tutor-message", "tutor-loading");
        messageList.addItem(responseMessage);
        scrollConversationToBottom();

        var uiOptional = getUI();
        var firstTokenReceived = new AtomicBoolean(false);

        uiOptional.ifPresent(ui -> chatService.chatStream(prompt, chatId)
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
                            scrollConversationToBottom();
                        }),
                        () -> ui.access(() -> {
                            responseMessage.removeClassNames("tutor-loading");
                            setLoadingState(false);
                            scrollConversationToBottom();
                        })));

        if (uiOptional.isEmpty()) {
            setLoadingState(false);
        }
    }

    private void scrollConversationToBottom() {
        messageList.getElement().executeJs("this.scrollIntoView({ block: 'end', behavior: 'smooth' });");
    }

    private void setLoadingState(boolean loading) {
        responseInProgress = loading;
        composerField.setEnabled(!loading);
        sendButton.setEnabled(!loading);
    }
}
