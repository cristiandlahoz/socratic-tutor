package com.wornux.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;

public class SidebarItem extends Span {

    public SidebarItem(Component icon, String text) {
        addClassNames("chat-sidebar-action-link-content", "sidebar-actions__item-content");

        icon.addClassNames("chat-sidebar-action-icon", "sidebar-actions__item-icon");

        var textElement = new Span(text);
        textElement.addClassName("sidebar-actions__item-text");

        add(icon, textElement);
    }
}
