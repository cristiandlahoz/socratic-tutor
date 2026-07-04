package com.wornux.ui.components.sidebar;

import java.util.List;
import java.util.UUID;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.internal.JacksonUtils;
import com.vaadin.flow.shared.Registration;
import com.wornux.dtos.chat.ConversationSummary;

@Tag("conversations-history")
@JsModule("./conversation/conversations-history.ts")
public final class ConversationsHistory extends Component implements HasSize {

    public ConversationsHistory() {
        setSizeFull();
    }

    public void setConversations(List<ConversationSummary> conversations) {
        var clientConversations = conversations.stream()
                .map(conversation -> new ClientConversation(
                    conversation.id().toString(),
                    conversation.title(),
                    conversation.updatedAt().toString()))
                .toList();
        getElement().setPropertyJson("conversations", JacksonUtils.listToJson(clientConversations));
    }

    public void setActiveConversationId(UUID activeConversationId) {
        getElement().setProperty(
            "activeConversationId",
            activeConversationId == null ? null : activeConversationId.toString());
    }

    public void setDisabled(boolean disabled) {
        getElement().setProperty("disabled", disabled);
    }

    public Registration addConversationOpenRequestedListener(
            ComponentEventListener<ConversationOpenRequestedEvent> listener) {
        return addListener(ConversationOpenRequestedEvent.class, listener);
    }

    private record ClientConversation(String id, String title, String updatedAt) {}

    @DomEvent("conversation-open-requested")
    public static final class ConversationOpenRequestedEvent extends ComponentEvent<ConversationsHistory> {

        private final UUID conversationId;

        public ConversationOpenRequestedEvent(
                ConversationsHistory source,
                boolean fromClient,
                @EventData("event.detail.conversationId") String conversationId) {
            super(source, fromClient);
            this.conversationId = UUID.fromString(conversationId);
        }

        public UUID getConversationId() {
            return conversationId;
        }
    }
}
