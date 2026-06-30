package com.wornux.ui.components.sidebar;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouterLink;
import com.wornux.ui.admin.SystemAdminWorkspaceView;
import com.wornux.ui.components.SidebarItem;
import com.wornux.ui.css.UiCss;
import com.wornux.ui.layout.MainLayoutAccess;
import com.wornux.ui.professor.ProfessorWorkspaceView;
import com.wornux.ui.student.StudentWorkspaceView;
import com.wornux.ui.tenant.TenantAdminWorkspaceView;

public class WorkspaceDrawerNavigation extends Div {

    public WorkspaceDrawerNavigation(MainLayoutAccess access) {
        UiCss.SIDEBAR_ACTIONS.addTo(this);

        var actions = new Div();
        UiCss.SIDEBAR_ACTIONS_LIST.addTo(actions);

        if (access.systemAdmin()) {
            actions.add(createNavigationButton(SystemAdminWorkspaceView.class, "Inicio", new Icon(VaadinIcon.HOME)));
        }
        if (access.tenantAdmin()) {
            actions.add(createNavigationButton(TenantAdminWorkspaceView.class, "Inicio", new Icon(VaadinIcon.HOME)));
        }
        if (access.professor()) {
            actions.add(createNavigationButton(ProfessorWorkspaceView.class, "Inicio", new Icon(VaadinIcon.HOME)));
        }
        if (access.student()) {
            actions.add(createNavigationButton(StudentWorkspaceView.class, "Inicio", new Icon(VaadinIcon.HOME)));
        }

        add(actions);
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
