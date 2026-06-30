package com.wornux.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.wornux.ui.css.UiCss;

public class SidebarItem extends Span {

    public SidebarItem(Component icon, String text) {
        UiCss.SIDEBAR_ACTIONS_ITEM_CONTENT.addTo(this);

        UiCss.SIDEBAR_ACTIONS_ITEM_ICON.addTo(icon);

        var textElement = new Span(text);
        UiCss.SIDEBAR_ACTIONS_ITEM_TEXT.addTo(textElement);

        add(icon, textElement);
    }
}
