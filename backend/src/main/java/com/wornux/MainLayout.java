package com.wornux;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.function.SerializableRunnable;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ListSignal;
import com.wornux.chat.ConversationSummary;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Layout
public class MainLayout extends AppLayout {

    private static final DateTimeFormatter CONVERSATION_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM · HH:mm", new Locale("es", "DO"));

    private final Button newChatButton;
    private final Div conversationList;
    private final Paragraph emptyHistory;
    private SerializableRunnable newChatAction;
    private SerializableConsumer<UUID> openConversationAction;

    public MainLayout() {
        addClassName("app-shell");
        setPrimarySection(Section.DRAWER);

        var toggle = new DrawerToggle();
        toggle.addThemeVariants(ButtonVariant.TERTIARY);
        toggle.addClassName("app-drawer-toggle");

        var drawerContent = new Div();
        drawerContent.addClassName("app-drawer-content");

        var title = new H1("Socratic Tutor");
        title.addClassName("chat-sidebar-title");

        var upperTitle = new HorizontalLayout(title, toggle);
        upperTitle.setPadding(false);
        upperTitle.setSpacing(false);
        upperTitle.setAlignItems(HorizontalLayout.Alignment.CENTER);
        upperTitle.addClassName("app-drawer-header");

        var divider = new Div();
        divider.addClassName("chat-sidebar-divider");

        var copy = new Paragraph(
                "Tutor conversacional diseñado para explorar ideas, resolver dudas y facilitar el aprendizaje mediante preguntas guiadas en el contexto de la introducción a la algoritmia.");
        copy.addClassName("chat-sidebar-copy");

        newChatButton = new Button("Nuevo chat");
        newChatButton.addThemeVariants(ButtonVariant.TERTIARY);
        newChatButton.addClassName("chat-sidebar-new-chat");
        newChatButton.addClickListener(_ -> newChatAction.run());

        emptyHistory = new Paragraph("Tus conversaciones apareceran aqui cuando empieces a chatear.");
        emptyHistory.addClassName("chat-sidebar-history-empty");

        conversationList = new Div();
        conversationList.addClassName("chat-sidebar-history");

        drawerContent.add(upperTitle, divider, copy, newChatButton, emptyHistory, conversationList);

        var drawerScroller = new Scroller(drawerContent);
        drawerScroller.setSizeFull();
        drawerScroller.addClassName("app-drawer-scroller");
        addToDrawer(drawerScroller);
    }

    public void setConversationActions(SerializableRunnable newChatAction,
                                       SerializableConsumer<UUID> openConversationAction) {
        this.newChatAction = newChatAction;
        this.openConversationAction = openConversationAction;
    }

    public void bindConversationState(ListSignal<ConversationSummary> conversations,
                                      Signal<UUID> activeConversationId,
                                      Signal<Boolean> disabled) {
        newChatButton.bindEnabled(Signal.not(disabled));
        emptyHistory.bindVisible(() -> conversations.get().isEmpty());
        conversationList.bindVisible(() -> !conversations.get().isEmpty());
        conversationList.bindChildren(conversations,
                conversationSignal -> createConversationItem(conversationSignal, activeConversationId, disabled));
    }

    private Component createConversationItem(Signal<ConversationSummary> conversationSignal,
                                             Signal<UUID> activeConversationId,
                                             Signal<Boolean> disabled) {
        var title = new Span();
        title.addClassName("chat-sidebar-history-title");
        title.bindText(conversationSignal.map(ConversationSummary::title));

        var timestamp = new Span();
        timestamp.addClassName("chat-sidebar-history-meta");
        timestamp.bindText(conversationSignal.map(this::formatTimestamp));

        var content = new Div(title, timestamp);
        content.addClassName("chat-sidebar-history-content");

        var button = new Button(content);
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.addClassName("chat-sidebar-history-item");
        button.bindClassName("chat-sidebar-history-item-active",
                () -> conversationSignal.get().id().equals(activeConversationId.get()));
        button.bindEnabled(() -> !disabled.get() && !conversationSignal.get().id().equals(activeConversationId.get()));
        button.addClickListener(_ -> openConversationAction.accept(conversationSignal.peek().id()));
        return button;
    }

    private String formatTimestamp(ConversationSummary conversation) {
        return CONVERSATION_TIME_FORMATTER.format(conversation.updatedAt().atZone(ZoneId.systemDefault()));
    }
}
