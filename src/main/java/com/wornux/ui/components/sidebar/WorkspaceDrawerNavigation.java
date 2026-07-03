package com.wornux.ui.components.sidebar;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouterLink;
import com.wornux.ui.components.SidebarItem;
import com.wornux.ui.css.UiCss;
import com.wornux.ui.navigation.NavigationEntry;

public class WorkspaceDrawerNavigation extends Div {

    public WorkspaceDrawerNavigation(List<NavigationEntry> entries) {
        UiCss.SIDEBAR_ACTIONS.addTo(this);

        var actions = new Div();
        UiCss.SIDEBAR_ACTIONS_LIST.addTo(actions);

        entries.forEach(entry -> actions.add(createNavigationButton(
                entry.routeTarget(),
                entry.label(),
                new Icon(iconFor(entry.label())))));

        add(actions);
    }

    private VaadinIcon iconFor(String label) {
        return switch (label) {
            case "Administración" -> VaadinIcon.HOME;
            case "Institución" -> VaadinIcon.INSTITUTION;
            case "Panel profesoral" -> VaadinIcon.ACADEMY_CAP;
            case "Panel estudiantil" -> VaadinIcon.USER;
            case "Matriz de roles" -> VaadinIcon.TABLE;
            case "Roles de tenant", "Roles de clase" -> VaadinIcon.KEY;
            case "Documentos" -> VaadinIcon.FILE_TEXT;
            case "Actividades" -> VaadinIcon.TASKS;
            default -> VaadinIcon.COMMENTS;
        };
    }

    private RouterLink createNavigationButton(
            Class<? extends Component> navigationTarget,
            String label,
            Component iconComponent) {
        var link = new RouterLink();
        link.setRoute(navigationTarget);
        UiCss.SIDEBAR_ACTIONS_ITEM_LINK.addTo(link);
        link.getElement().setAttribute("aria-label", label);
        link.add(new SidebarItem(iconComponent, label));
        return link;
    }
}
