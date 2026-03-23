package com.wornux;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.router.Layout;

@Layout
public class MainLayout extends AppLayout {

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

        var copy = new Paragraph("Tutor conversacional para explorar ideas, resolver dudas y aprender con preguntas guiadas.");
        copy.addClassName("chat-sidebar-copy");

        drawerContent.add(upperTitle, divider, copy);

        var drawerScroller = new Scroller(drawerContent);
        drawerScroller.setSizeFull();
        drawerScroller.addClassName("app-drawer-scroller");
        addToDrawer(drawerScroller);
    }
}
