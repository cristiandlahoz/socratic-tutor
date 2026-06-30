package com.wornux.ui.components.sidebar;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.signals.Signal;
import com.wornux.ui.components.SidebarItem;
import com.wornux.ui.conversation.ConversationState;
import com.wornux.ui.css.UiCss;
import com.wornux.ui.conversation.ConversationViewModel;
import com.wornux.ui.ingestion.DocumentIngestionView;
import com.wornux.ui.layout.MainLayoutAccess;
import com.wornux.ui.training_activity.TrainingActivityView;

public class ChatDrawerActions extends Div {

    private final NativeButton newConversationButton;

    public ChatDrawerActions(MainLayoutAccess access, ConversationState state, ConversationViewModel viewModel) {
        UiCss.SIDEBAR_ACTIONS.addTo(this);

        var actions = new Div();
        UiCss.SIDEBAR_ACTIONS_LIST.addTo(actions);

        newConversationButton = createActionButton(viewModel);
        newConversationButton.setId("sidebar-new-conversation-action");

        if (access.canChat()) {
            actions.add(newConversationButton);
        }
        if (access.canManageDocuments()) {
            var ingestDocumentButton = createNavigationButton(
                DocumentIngestionView.class,
                "Ingestar PDF",
                new Icon(VaadinIcon.UPLOAD_ALT));
            ingestDocumentButton.setId("sidebar-ingest-document-link");
            actions.add(ingestDocumentButton);
        }
        if (access.canManageActivities()) {
            var evaluationButton = createNavigationButton(
                TrainingActivityView.class,
                "Actividades formativas",
                new SvgIcon("/icons/pencil.svg"));
            evaluationButton.setId("sidebar-evaluation-link");
            actions.add(evaluationButton);
        }

        Signal.effect(newConversationButton, () -> newConversationButton.setEnabled(!state.responseInProgress().get()));
        add(actions);
    }

    private NativeButton createActionButton(ConversationViewModel viewModel) {
        var button = new NativeButton();
        UiCss.SIDEBAR_ACTIONS_ITEM_BUTTON.addTo(button);
        button.add(new SidebarItem(new Icon(VaadinIcon.PLUS), "Nueva conversación"));
        button.setAriaLabel("Nueva conversación");
        button.addClickListener(_ -> viewModel.onStartNewConversation());
        return button;
    }

    private RouterLink createNavigationButton(
            Class<? extends Component> navigationTarget,
            String label,
            Component iconComponent) {
        var link = new RouterLink();
        link.setRoute(navigationTarget);
        UiCss.SIDEBAR_ACTIONS_ITEM_LINK.addTo(link);
        link.getElement().setAttribute("aria-label", label);
        link.add(new SidebarItem(iconComponent, label));
        return link;
    }
}
