package com.wornux;

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
import com.wornux.chat.ConversationSummary;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Layout
public class MainLayout extends AppLayout {

    private static final DateTimeFormatter CONVERSATION_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM · HH:mm", new Locale("es", "DO"));

    private final Button newChatButton;
    private final Div conversationList;
    private SerializableRunnable newChatAction = () -> {
    };
    private SerializableConsumer<UUID> openConversationAction = _ -> {
    };

    public MainLayout() {
        addClassName("app-shell");
        setPrimarySection(Section.DRAWER);

        var toggle = new DrawerToggle();
        toggle.addThemeVariants(ButtonVariant.AURA_TERTIARY);
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
        newChatButton.addThemeVariants(ButtonVariant.AURA_TERTIARY);
        newChatButton.addClassName("chat-sidebar-new-chat");
        newChatButton.addClickListener(_ -> newChatAction.run());

        conversationList = new Div();
        conversationList.addClassName("chat-sidebar-history");

        drawerContent.add(upperTitle, divider, copy, newChatButton, conversationList);

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

    public void updateConversationHistory(List<ConversationSummary> conversations,
                                          UUID activeConversationId,
                                          boolean disabled) {
        conversationList.removeAll();
        newChatButton.setEnabled(!disabled);

        if (conversations.isEmpty()) {
            var emptyHistory = new Paragraph("Tus conversaciones apareceran aqui cuando empieces a chatear.");
            emptyHistory.addClassName("chat-sidebar-history-empty");
            conversationList.add(emptyHistory);
            return;
        }

        for (ConversationSummary conversation : conversations) {
            var title = new Span(conversation.title());
            title.addClassName("chat-sidebar-history-title");

            var timestamp = new Span(formatTimestamp(conversation));
            timestamp.addClassName("chat-sidebar-history-meta");

            var content = new Div(title, timestamp);
            content.addClassName("chat-sidebar-history-content");

            var button = new Button(content);
            button.addThemeVariants(ButtonVariant.AURA_TERTIARY);
            button.addClassName("chat-sidebar-history-item");
            if (conversation.id().equals(activeConversationId)) {
                button.addClassName("chat-sidebar-history-item-active");
            }
            button.setEnabled(!disabled && !conversation.id().equals(activeConversationId));
            button.addClickListener(_ -> openConversationAction.accept(conversation.id()));

            conversationList.add(button);
        }
    }

    private String formatTimestamp(ConversationSummary conversation) {
        return CONVERSATION_TIME_FORMATTER.format(conversation.updatedAt().atZone(ZoneId.systemDefault()));
    }
}
