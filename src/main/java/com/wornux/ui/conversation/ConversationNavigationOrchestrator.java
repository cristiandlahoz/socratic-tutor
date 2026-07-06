package com.wornux.ui.conversation;

import java.util.UUID;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import org.springframework.stereotype.Component;

@Component
public class ConversationNavigationOrchestrator {

    public void openConversation(String conversationParameter, UUID conversationId) {
        UI.getCurrent()
                .navigate(ConversationView.class, QueryParameters.of(conversationParameter, conversationId.toString()));
    }

    public void openNewConversation() {
        UI.getCurrent().navigate(ConversationView.class);
    }

    public void synchronizeAddressBar(String conversationParameter, UUID conversationId) {
        UI.getCurrent()
                .getPage()
                .getHistory()
                .replaceState(
                    null,
                    new Location("chat", QueryParameters.of(conversationParameter, conversationId.toString())));
    }
}
