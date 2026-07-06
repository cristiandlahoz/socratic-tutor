package com.wornux.ui.components.sidebar;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.RouterLink;
import com.wornux.ui.components.SidebarItem;
import com.wornux.ui.css.UiCss;
import com.wornux.ui.navigation.NavigationEntry;

public class WorkspaceDrawerNavigation extends Div {

    public WorkspaceDrawerNavigation(List<NavigationEntry> entries) {
        UiCss.SIDEBAR_ACTIONS.addTo(this);

        var actions = new Div();
        UiCss.SIDEBAR_ACTIONS_LIST.addTo(actions);

        entries.forEach(
            entry -> actions.add(createNavigationButton(entry.routeTarget(), entry.label(), entry.createIcon())));

        add(actions);
    }

    private RouterLink createNavigationButton(
            NavigationEntry.RouteTarget navigationTarget,
            String label,
            Component iconComponent) {
        var link = new RouterLink();
        navigationTarget.setRouteOn(link);
        UiCss.SIDEBAR_ACTIONS_ITEM_LINK.addTo(link);
        link.getElement().setAttribute("aria-label", label);
        link.add(new SidebarItem(iconComponent, label));
        return link;
    }
}
