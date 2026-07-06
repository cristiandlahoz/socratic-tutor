package com.wornux.ui.navigation;

import java.util.function.Supplier;

import com.vaadin.flow.component.Component;
import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.security.permission.AppPermission;

public record NavigationEntry(String label, Class<? extends Component> routeTarget, Supplier<Component> iconFactory,
        ContextLevel minimumContextLevel, AppPermission requiredPermission, int order) {

    public Component createIcon() {
        return iconFactory.get();
    }
}
