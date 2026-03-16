package com.wornux.chat;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author @github/cristiandlahoz
 */
@Route("")
public class ChatView extends Composite<VerticalLayout> {

    private final ChatService chatService;
    private final MessageList messageList;
    private final MessageInput messageInput;
    private final Div typingIndicator;
    private final String chatId = UUID.randomUUID().toString();
    private boolean responseInProgress;

    public ChatView(ChatService chatService) {
        this.chatService = chatService;

        getContent().setWidthFull();

        var title = new H1("Chat");
        getContent().add(title);
        messageList = new MessageList();
        messageList.setMarkdown(true);

        var scroller = new Scroller(messageList);
        scroller.setWidthFull();
        getContent().addAndExpand(scroller);

        typingIndicator = createTypingIndicator();
        typingIndicator.setVisible(false);
        getContent().add(typingIndicator);

        messageInput = new MessageInput();
        messageInput.setWidth("70%");
        messageInput.addSubmitListener(this::onSubmit);
        getContent().add(messageInput);
        getContent().setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, typingIndicator);
        getContent().setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, messageInput);
        getContent().setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, title);

    }

    private void onSubmit(MessageInput.SubmitEvent submitEvent) {
        if (responseInProgress) {
            return;
        }

        setLoadingState(true);

        var promptMessage = new MessageListItem(submitEvent.getValue(), Instant.now(), "You");
        promptMessage.setUserColorIndex(0);
        messageList.addItem(promptMessage);

        var responseMessage = new MessageListItem("", Instant.now(), "Socratic Tutor");
        responseMessage.setUserColorIndex(1);
        messageList.addItem(responseMessage);

        var userPrompt = submitEvent.getValue();
        var uiOptional = submitEvent.getSource().getUI();
        var firstTokenReceived = new AtomicBoolean(false);

        uiOptional.ifPresent(ui -> chatService.chatStream(userPrompt, chatId)
                .subscribe(token -> ui.access(() -> {
                            if (firstTokenReceived.compareAndSet(false, true)) {
                                typingIndicator.setVisible(false);
                            }
                            responseMessage.appendText(token);
                        }),
                        _ -> ui.access(() -> {
                            if (responseMessage.getText().isBlank()) {
                                responseMessage.setText("Sorry, something went wrong while generating the response. Please try again.");
                            }
                            setLoadingState(false);
                        }),
                        () -> ui.access(() -> setLoadingState(false))));

        if (uiOptional.isEmpty()) {
            setLoadingState(false);
        }

    }

    private Div createTypingIndicator() {
        var indicator = new Div();
        indicator.addClassName("chat-typing-indicator");
        indicator.getElement().setAttribute("aria-live", "polite");

        var label = new Span("Socratic Tutor is thinking");
        label.addClassName("chat-typing-label");

        var dots = new Span();
        dots.addClassName("chat-typing-dots");
        dots.getElement().setAttribute("aria-hidden", "true");
        dots.add(new Span(), new Span(), new Span());

        indicator.add(label, dots);
        return indicator;
    }

    private void setLoadingState(boolean loading) {
        responseInProgress = loading;
        messageInput.setEnabled(!loading);
        typingIndicator.setVisible(loading);

    }
}
