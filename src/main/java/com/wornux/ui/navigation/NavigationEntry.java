package com.wornux.ui.navigation;

import com.vaadin.flow.component.Component;
import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.security.permission.AppPermission;

public record NavigationEntry(
        String label,
        Class<? extends Component> routeTarget,
        ContextLevel minimumContextLevel,
        AppPermission requiredPermission,
        int order) {
}
