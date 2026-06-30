package com.wornux.ui.css;

import com.vaadin.flow.component.Component;

public record CssClass(String value) {

    public void addTo(Component component) {
        component.getElement().getClassList().add(value);
    }

    public void addTo(Component component, boolean enabled) {
        component.getElement().getClassList().set(value, enabled);
    }

}
