package com.wornux;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.signals.Signal;
import com.wornux.chat.ChatUiController;
import com.wornux.chat.ChatUiState;
import com.wornux.chat.ConversationSummary;
import org.jspecify.annotations.NonNull;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Layout
@PreserveOnRefresh
public class MainLayout extends AppLayout {

    private static final DateTimeFormatter CONVERSATION_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.of("es", "DO"));

    private final Button newChatButton;
    private final Div conversationList;
    private final Paragraph emptyHistory;

    public MainLayout(ChatUiState state, ChatUiController controller) {
        setPrimarySection(Section.DRAWER);

        var drawerContent = new Div();
        drawerContent.addClassName("shell-drawer-content");

        var title = new H1("Socratic Tutor");
        title.addClassName("chat-sidebar-title");

        var infoButton = getInfoButton();

        var infoPopover = new Popover();
        infoPopover.setTarget(infoButton);
        infoPopover.setModal(false);
        infoPopover.addClassName("chat-sidebar-help-popover");

        var infoTitle = new Span("Como funciona");
        infoTitle.addClassName("chat-sidebar-help-title");

        var infoCopy = new Paragraph(
                "Tutor conversacional para explorar ideas, resolver dudas y facilitar el aprendizaje con preguntas guiadas en introducción a la algoritmia.");
        infoCopy.addClassName("chat-sidebar-help-description");
        infoPopover.add(new Div(infoTitle, infoCopy));

        var titleRow = new HorizontalLayout(title, infoButton);
        titleRow.setPadding(false);
        titleRow.setSpacing(false);
        titleRow.setAlignItems(HorizontalLayout.Alignment.CENTER);
        titleRow.addClassName("chat-sidebar-header-row");

        var upperTitle = new HorizontalLayout(titleRow);
        upperTitle.setPadding(false);
        upperTitle.setSpacing(false);
        upperTitle.setAlignItems(HorizontalLayout.Alignment.CENTER);
        upperTitle.setWidthFull();
        upperTitle.addClassName("shell-drawer-header");

        var topSection = new Div(upperTitle);
        topSection.addClassName("chat-sidebar-header");

        newChatButton = new Button("Nuevo chat");
        newChatButton.addThemeVariants(ButtonVariant.TERTIARY);
        newChatButton.addClassName("chat-sidebar-new-button");
        newChatButton.addClickListener(_ -> controller.startNewChat());
        topSection.add(newChatButton);

        var sectionLabel = new Span("Conversaciones");
        sectionLabel.addClassName("chat-sidebar-section-title");

        emptyHistory = new Paragraph("Aun no tienes conversaciones.");
        conversationList = new Div();

        drawerContent.add(topSection, sectionLabel, emptyHistory, conversationList);

        var drawerScroller = new Scroller(drawerContent);
        drawerScroller.setSizeFull();
        drawerScroller.addClassName("shell-drawer-scroller");
        addToDrawer(drawerScroller);

        bindConversationState(state, controller);
    }

    private static @NonNull Button getInfoButton() {
        var icon = new Icon(VaadinIcon.INFO_CIRCLE_O);
        icon.setSize("0.95rem");

        var infoButton = new Button(icon);
        infoButton.addThemeVariants(ButtonVariant.TERTIARY);
        infoButton.getStyle().setColor("var(--chat-text-secondary)");
        infoButton.setAriaLabel("Acerca del tutor");
        return infoButton;
    }

    private void bindConversationState(ChatUiState state, ChatUiController controller) {
        var conversations = state.conversationHistory();
        var activeConversationId = state.activeConversationId().asReadonly();
        var disabled = state.responseInProgress().asReadonly();

        newChatButton.bindEnabled(Signal.not(disabled));
        emptyHistory.bindVisible(() -> conversations.get().isEmpty());
        conversationList.bindVisible(() -> !conversations.get().isEmpty());
        conversationList.bindChildren(conversations,
                conversationSignal -> createConversationItem(conversationSignal, activeConversationId, disabled, controller));
    }

    private Component createConversationItem(Signal<ConversationSummary> conversationSignal,
                                             Signal<UUID> activeConversationId,
                                             Signal<Boolean> disabled,
                                             ChatUiController controller) {
        var title = new Span();
        title.addClassName("chat-sidebar-item-title");
        title.bindText(conversationSignal.map(ConversationSummary::title));

        var timestamp = new Span();
        timestamp.addClassName("chat-sidebar-item-meta");
        timestamp.bindText(conversationSignal.map(this::formatTimestamp));

        var content = new Div(title, timestamp);
        content.addClassName("chat-sidebar-item");

        var button = new Button(content);
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setWidthFull();
        button.bindEnabled(() -> !disabled.get() && !conversationSignal.get().id().equals(activeConversationId.get()));
        button.addClickListener(_ -> controller.openConversation(conversationSignal.peek().id()));
        return button;
    }

    private String formatTimestamp(ConversationSummary conversation) {
        return CONVERSATION_TIME_FORMATTER.format(conversation.updatedAt().atZone(ZoneId.systemDefault()));
    }
}
