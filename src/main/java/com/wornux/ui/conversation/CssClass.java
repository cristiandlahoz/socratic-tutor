package com.wornux.ui.conversation;

import com.vaadin.flow.component.HasStyle;

public record CssClass(String value) {

    public void addTo(HasStyle component) {
        component.addClassName(value);
    }

    public void addTo(HasStyle component, boolean enabled) {
        component.setClassName(value, enabled);
    }

    public void addTo(CodeMessageListItem item) {
        item.addClassNames(value);
    }
}
