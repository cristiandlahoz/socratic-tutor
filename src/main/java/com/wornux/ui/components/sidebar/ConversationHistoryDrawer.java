package com.wornux.ui.components.sidebar;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.signals.Signal;
import com.wornux.ui.conversation.ConversationState;
import com.wornux.ui.conversation.ConversationViewModel;

public class ConversationHistoryDrawer extends Div {

    public ConversationHistoryDrawer(ConversationState state, ConversationViewModel viewModel) {
        var history = new ConversationsHistory();
        history.addConversationOpenRequestedListener(event -> viewModel.onOpenConversation(event.getConversationId()));

        Signal.effect(
            history,
            () -> {
                history.setConversations(state.conversationHistory().get().stream().map(Signal::get).toList());
                history.setActiveConversationId(state.activeConversationId().get());
                history.setDisabled(state.responseInProgress().get());
            });

        add(history);
    }
}
