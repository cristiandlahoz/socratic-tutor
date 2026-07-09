package com.wornux.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.wornux.ui.css.UiCss;

public abstract class WorkspaceViewShell extends VerticalLayout {

    protected WorkspaceViewShell() {
        UiCss.WORKSPACE_VIEW.addTo(this);
    }

    protected void setWorkspaceContent(String title, String description, Component toolbar, Component... content) {
        removeAll();
        add(createHeader(title, description));
        if (toolbar != null) {
            add(toolbar);
        }
        add(content);
    }

    protected Div createHeader(String title, String description) {
        var heading = new H1(title);
        var copy = new Paragraph(description);
        var header = new Div(heading, copy);
        UiCss.WORKSPACE_HERO.addTo(header);
        UiCss.WORKSPACE_HERO_PLAIN.addTo(header);
        return header;
    }

    protected Button primaryButton(String label, Runnable action) {
        var button = new Button(label, _ -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return button;
    }

    protected Button secondaryButton(String label, Runnable action) {
        return new Button(label, _ -> action.run());
    }

    protected HorizontalLayout toolbar(Component... children) {
        var toolbar = new HorizontalLayout(children);
        UiCss.WORKSPACE_GRID_TOOLBAR.addTo(toolbar);
        toolbar.setPadding(false);
        toolbar.setMargin(false);
        toolbar.setSpacing(false);
        return toolbar;
    }

    protected HorizontalLayout formRow(Component... children) {
        var row = new HorizontalLayout(children);
        UiCss.WORKSPACE_FORM_ROW.addTo(row);
        row.setPadding(false);
        row.setMargin(false);
        row.setSpacing(false);
        return row;
    }

    protected void addWorkspaceFieldClasses(Component... fields) {
        for (var field : fields) {
            UiCss.WORKSPACE_FIELD.addTo(field);
        }
    }
}
