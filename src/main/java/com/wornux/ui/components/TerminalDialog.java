package com.wornux.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dialog.Dialog;
import com.wornux.ui.css.UiCss;

public class TerminalDialog extends Dialog {

    private final TerminalCard card;

    public TerminalDialog(String label, String title, String description, Component content) {
        UiCss.TERMINAL_DIALOG.addTo(this);
        setModal(true);
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false);

        card = new TerminalCard(label, title, description, content);
        UiCss.TERMINAL_DIALOG_CARD.addTo(card);
        add(card);
    }

    public void addActions(Component... components) {
        card.addActions(components);
    }
}
