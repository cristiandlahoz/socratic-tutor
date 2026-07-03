package com.wornux.ui.rbac;

import java.util.Set;
import java.util.UUID;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.authorization.Role;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.authorization.RoleAdministrationService;
import com.wornux.security.permission.AppPermission;
import com.wornux.ui.MainLayout;
import jakarta.annotation.security.PermitAll;

@Route(value = "roles/tenant-assignments", layout = MainLayout.class)
@PageTitle("Asignación de roles institucionales")
@PermitAll
@RequiresPermission(AppPermission.ROLE_ASSIGN)
public class TenantMemberRoleAssignmentView extends VerticalLayout {

    private final RoleAdministrationService roleAdministrationService;
    private final Grid<TenantAccount> grid = new Grid<>(TenantAccount.class, false);

    public TenantMemberRoleAssignmentView(RoleAdministrationService roleAdministrationService) {
        this.roleAdministrationService = roleAdministrationService;
        setSizeFull();
        add(new H1("Asignación de roles institucionales"), new Paragraph("Asigna roles TENANT activos y asignables a miembros del tenant activo."), grid);
        refresh();
    }

    private void refresh() {
        grid.removeAllColumns();
        var matrix = roleAdministrationService.tenantAssignments();
        grid.addColumn(member -> displayName(member)).setHeader("Miembro").setAutoWidth(true);
        for (var role : matrix.roles()) {
            grid.addComponentColumn(member -> checkbox(member.getId(), role)).setHeader(role.getName()).setAutoWidth(true);
        }
        grid.setItems(matrix.members());
    }

    private Checkbox checkbox(UUID tenantAccountId, Role role) {
        Set<UUID> assignedRoles = roleAdministrationService.tenantAccountRoleIds(tenantAccountId);
        var checkbox = new Checkbox();
        checkbox.setValue(assignedRoles.contains(role.getId()));
        checkbox.setTooltipText("assignment_level=TENANT; no cambia membresía académica");
        checkbox.addValueChangeListener(event -> {
            if (!event.isFromClient()) {
                return;
            }
            try {
                roleAdministrationService.setTenantRole(tenantAccountId, role.getId(), event.getValue());
                Notification.show("Asignación actualizada");
            }
            catch (RuntimeException ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
                checkbox.setValue(!event.getValue());
            }
        });
        return checkbox;
    }

    private String displayName(TenantAccount member) {
        var account = member.getAccount();
        var name = "%s %s".formatted(account.getFirstName() == null ? "" : account.getFirstName(), account.getLastName() == null ? "" : account.getLastName()).trim();
        return name.isBlank() ? account.getEmail() : "%s <%s>".formatted(name, account.getEmail());
    }
}
