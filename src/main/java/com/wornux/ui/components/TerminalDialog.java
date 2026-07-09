package com.wornux.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.wornux.ui.css.UiCss;

public class TerminalDialog extends Dialog {

    private final HorizontalLayout actions = new HorizontalLayout();

    public TerminalDialog(String label, String title, String description, Component content) {
        UiCss.TERMINAL_DIALOG.addTo(this);
        setCloseOnEsc(true);
        setCloseOnOutsideClick(true);

        var heading = new H2(title);
        var copy = new Paragraph(description);

        UiCss.TERMINAL_DIALOG_ACTIONS.addTo(actions);
        actions.setPadding(false);
        actions.setMargin(false);
        actions.setSpacing(false);

        var card = new Div(heading, copy, content, actions);
        card.getElement().setAttribute("data-terminal-label", label);
        UiCss.TERMINAL_DIALOG_CARD.addTo(card);
        add(card);
    }

    public void addActions(Component... components) {
        actions.add(components);
    }
}
