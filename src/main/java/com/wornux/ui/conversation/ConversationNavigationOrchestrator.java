package com.wornux.ui.conversation;

import java.util.UUID;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import org.springframework.stereotype.Component;

@Component
public class ConversationNavigationOrchestrator {

    public void openConversation(UUID conversationId) {
        UI.getCurrent()
                .navigate(ConversationView.class,
                    new RouteParameters(
                        ConversationView.THREAD_ROUTE_PARAMETER,
                        ConversationViewModel.toPublicThreadId(conversationId)));
    }

    public void openNewConversation() {
        UI.getCurrent().navigate(ConversationView.class);
    }

    public void synchronizeAddressBar(UUID conversationId) {
        UI.getCurrent()
                .getPage()
                .getHistory()
                .replaceState(
                    null,
                    new Location(ConversationView.ROUTE + "/" + ConversationViewModel.toPublicThreadId(conversationId)));
    }
}
