package com.wornux.ui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.workspace.SystemAdminWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import jakarta.annotation.security.PermitAll;

@Route(value = "admin", autoLayout = false)
@PageTitle("System admin workspace")
@PermitAll
public class SystemAdminWorkspaceView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthenticatedAccountService authenticatedAccountService;
    private final WorkspaceRoutingService workspaceRoutingService;
    private final SystemAdminWorkspaceService systemAdminWorkspaceService;
    private final Grid<com.wornux.data.entities.identity.Tenant> tenantGrid =
            new Grid<>(com.wornux.data.entities.identity.Tenant.class, false);
    private final TextField tenantNameField = new TextField("Tenant name");
    private final EmailField inviteEmailField = new EmailField("Tenant admin email");

    public SystemAdminWorkspaceView(
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService,
            SystemAdminWorkspaceService systemAdminWorkspaceService) {
        this.authenticatedAccountService = authenticatedAccountService;
        this.workspaceRoutingService = workspaceRoutingService;
        this.systemAdminWorkspaceService = systemAdminWorkspaceService;

        addClassName("workspace-view");
        tenantGrid.addColumn(com.wornux.data.entities.identity.Tenant::getName).setHeader("Tenant");
        tenantGrid.addColumn(tenant -> tenant.getOwnerTenantAccount() == null ? "Unassigned" : "Assigned")
                .setHeader("Owner status");
        add(
            new H1("System admin workspace"),
            new HorizontalLayout(tenantNameField, new Button("Create tenant", _ -> onCreateTenant())),
            new HorizontalLayout(inviteEmailField, new Button("Invite tenant admin", _ -> onInviteTenantAdmin())),
            tenantGrid);
        refresh();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.SYSTEM_ADMIN)) {
            event.forwardTo("no-access");
        }
    }

    private void onCreateTenant() {
        try {
            systemAdminWorkspaceService
                    .createTenant(authenticatedAccountService.requireCurrentAccount(), tenantNameField.getValue());
            tenantNameField.clear();
            refresh();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void onInviteTenantAdmin() {
        var tenant = tenantGrid.asSingleSelect().getValue();
        if (tenant == null) {
            Notification.show("Select a tenant first.");
            return;
        }
        try {
            systemAdminWorkspaceService.inviteTenantAdmin(
                authenticatedAccountService.requireCurrentAccount(),
                tenant.getId(),
                inviteEmailField.getValue());
            inviteEmailField.clear();
            Notification.show("Invitation sent.");
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void refresh() {
        tenantGrid.setItems(systemAdminWorkspaceService.listTenants());
    }
}
