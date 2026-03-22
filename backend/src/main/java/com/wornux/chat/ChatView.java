package com.wornux.chat;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.H1;
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

        messageInput = new MessageInput();
        messageInput.setWidth("70%");
        messageInput.addSubmitListener(this::onSubmit);
        getContent().add(messageInput);
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
        promptMessage.addClassNames("user-message");
        messageList.addItem(promptMessage);

        var responseMessage = new MessageListItem("", Instant.now(), "Socratic Tutor");
        responseMessage.setUserColorIndex(1);
        responseMessage.addClassNames("tutor-message", "tutor-loading");
        messageList.addItem(responseMessage);

        var userPrompt = submitEvent.getValue();
        var uiOptional = submitEvent.getSource().getUI();
        var firstTokenReceived = new AtomicBoolean(false);

        uiOptional.ifPresent(ui -> chatService.chatStream(userPrompt, chatId)
                .subscribe(token -> ui.access(() -> {
                            if (firstTokenReceived.compareAndSet(false, true)) {
                                responseMessage.removeClassNames("tutor-loading");
                            }
                            responseMessage.appendText(token);
                        }),
                        _ -> ui.access(() -> {
                            responseMessage.removeClassNames("tutor-loading");
                            if (responseMessage.getText().isBlank()) {
                                responseMessage.setText("Sorry, something went wrong while generating the response. Please try again.");
                            }
                            setLoadingState(false);
                        }),
                        () -> ui.access(() -> {
                            responseMessage.removeClassNames("tutor-loading");
                            setLoadingState(false);
                        })));

        if (uiOptional.isEmpty()) {
            setLoadingState(false);
        }

    }

    private void setLoadingState(boolean loading) {
        responseInProgress = loading;
        messageInput.setEnabled(!loading);

    }
}
