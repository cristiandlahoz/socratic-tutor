package com.wornux.presentation.chat;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatNavigationOrchestrator {

  public void openConversation(String conversationParameter, UUID conversationId) {
    UI.getCurrent()
        .navigate(ChatView.class, QueryParameters.of(conversationParameter, conversationId.toString()));
  }

  public void openDraft(String draftParameter, String draftValue) {
    UI.getCurrent().navigate(ChatView.class, QueryParameters.of(draftParameter, draftValue));
  }

  public void synchronizeAddressBar(String conversationParameter, UUID conversationId) {
    UI.getCurrent()
        .getPage()
        .getHistory()
        .replaceState(
            null,
            new Location("", QueryParameters.of(conversationParameter, conversationId.toString())));
  }
}
