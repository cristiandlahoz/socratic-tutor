package com.wornux.ui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
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
        tenantNameField.addClassName("workspace-field");
        inviteEmailField.addClassName("workspace-field");
        tenantGrid.addColumn(com.wornux.data.entities.identity.Tenant::getName).setHeader("Tenant");
        tenantGrid.addColumn(tenant -> tenant.getOwnerTenantAccount() == null ? "Unassigned" : "Assigned")
                .setHeader("Owner status");
        tenantGrid.addClassName("workspace-grid");
        tenantGrid.setWidthFull();

        add(
            createHeader(
                "System admin workspace",
                "Create tenant spaces, select the right account, and delegate ownership without leaving the academic control surface."),
            createSection(
                "Tenant setup",
                "Create an institution shell before inviting the person who will manage it.",
                formRow(tenantNameField, primaryButton("Create tenant", this::onCreateTenant)),
                formRow(inviteEmailField, primaryButton("Invite tenant admin", this::onInviteTenantAdmin))),
            createSection("Tenants", "Select a tenant before sending an admin invitation.", tenantGrid));
        refresh();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.SYSTEM_ADMIN)) {
            event.forwardTo("no-access");
        }
    }

    private Div createHeader(String title, String description) {
        var heading = new H1(title);
        var copy = new Paragraph(description);
        var header = new Div(heading, copy);
        header.addClassName("workspace-hero");
        return header;
    }

    private Div createSection(String title, String description, com.vaadin.flow.component.Component... children) {
        var heading = new H2(title);
        var copy = new Paragraph(description);
        var sectionHeader = new Div(heading, copy);
        sectionHeader.addClassName("workspace-section-header");
        var section = new Div(sectionHeader);
        section.add(children);
        section.addClassName("workspace-section");
        return section;
    }

    private HorizontalLayout formRow(com.vaadin.flow.component.Component... children) {
        var row = new HorizontalLayout(children);
        row.addClassName("workspace-form-row");
        row.setPadding(false);
        row.setMargin(false);
        row.setSpacing(false);
        return row;
    }

    private Button primaryButton(String label, Runnable action) {
        var button = new Button(label, _ -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return button;
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
