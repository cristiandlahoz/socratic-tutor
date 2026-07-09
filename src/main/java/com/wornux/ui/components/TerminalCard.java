package com.wornux.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.wornux.ui.css.UiCss;

public class TerminalCard extends Div {

    private final H2 heading = new H2();
    private final Paragraph copy = new Paragraph();
    private final Div body = new Div();
    private final HorizontalLayout actions = new HorizontalLayout();

    public TerminalCard(String label, String title, String description, Component... content) {
        UiCss.TERMINAL_CARD.addTo(this);
        UiCss.TERMINAL_CARD_BODY.addTo(body);
        UiCss.TERMINAL_DIALOG_ACTIONS.addTo(actions);
        actions.setPadding(false);
        actions.setMargin(false);
        actions.setSpacing(false);
        add(heading, copy, body, actions);
        setContent(label, title, description, content);
    }

    public void setContent(String label, String title, String description, Component... content) {
        getElement().setAttribute("data-terminal-label", label);
        heading.setText(title);
        copy.setText(description);
        body.removeAll();
        body.add(content);
        actions.removeAll();
    }

    public void addActions(Component... components) {
        actions.add(components);
    }
}
