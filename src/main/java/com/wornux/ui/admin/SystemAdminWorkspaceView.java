package com.wornux.ui.admin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.identity.Tenant;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.workspace.SystemAdminWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.auth.NoAccessView;
import jakarta.annotation.security.PermitAll;
import com.wornux.ui.css.UiCss;

@Route(value = "admin", layout = MainLayout.class)
@PageTitle("Espacio de administración del sistema")
@PermitAll
public class SystemAdminWorkspaceView extends VerticalLayout implements BeforeEnterObserver {

    private enum AdminFilter {
        ALL("Todas"),
        WITHOUT_ADMIN("Sin administrador"),
        WITH_ADMIN("Con administrador activo");

        private final String label;

        AdminFilter(String label) {
            this.label = label;
        }
    }

    private final AuthenticatedAccountService authenticatedAccountService;
    private final WorkspaceRoutingService workspaceRoutingService;
    private final SystemAdminWorkspaceService systemAdminWorkspaceService;
    private final Grid<Tenant> tenantGrid = new Grid<>(Tenant.class, false);
    private final TextField searchField = new TextField("Buscar institución");
    private final Select<AdminFilter> adminFilter = new Select<>();
    private GridListDataView<Tenant> tenantDataView;

    public SystemAdminWorkspaceView(
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService,
            SystemAdminWorkspaceService systemAdminWorkspaceService) {
        this.authenticatedAccountService = authenticatedAccountService;
        this.workspaceRoutingService = workspaceRoutingService;
        this.systemAdminWorkspaceService = systemAdminWorkspaceService;

        UiCss.WORKSPACE_VIEW.addTo(this);
        configureGrid();

        add(
            createHeader(
                "Espacio de administración del sistema",
                "Gestiona las instituciones desde una sola tabla. Crea el espacio institucional y envía la invitación cuando ya tengas definida a la persona administradora."),
            createToolbar(),
            tenantGrid);
        refresh();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.SYSTEM_ADMIN)) {
            event.forwardTo(NoAccessView.class);
        }
    }

    private Div createHeader(String title, String description) {
        var heading = new H1(title);
        var copy = new Paragraph(description);
        var header = new Div(heading, copy);
        UiCss.WORKSPACE_HERO.addTo(header);
        UiCss.WORKSPACE_HERO_PLAIN.addTo(header);
        return header;
    }

    private Component createToolbar() {
        UiCss.WORKSPACE_FIELD.addTo(searchField);
        searchField.setPlaceholder("Nombre de la institución");
        searchField.setClearButtonVisible(true);
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(_ -> applyFilters());

        UiCss.WORKSPACE_FIELD.addTo(adminFilter);
        adminFilter.setLabel("Filtro");
        adminFilter.setItems(AdminFilter.values());
        adminFilter.setItemLabelGenerator(filter -> filter.label);
        adminFilter.setValue(AdminFilter.ALL);
        adminFilter.addValueChangeListener(_ -> applyFilters());

        var createButton = primaryButton("Crear institución", this::openCreateTenantDialog);
        createButton.setIcon(new Icon(VaadinIcon.PLUS));

        var toolbar = new HorizontalLayout(searchField, adminFilter, createButton);
        UiCss.WORKSPACE_GRID_TOOLBAR.addTo(toolbar);
        toolbar.setPadding(false);
        toolbar.setMargin(false);
        toolbar.setSpacing(false);
        return toolbar;
    }

    private void configureGrid() {
        UiCss.WORKSPACE_GRID.addTo(tenantGrid);
        UiCss.WORKSPACE_TENANT_GRID.addTo(tenantGrid);
        tenantGrid.setWidthFull();
        tenantGrid.setSelectionMode(Grid.SelectionMode.NONE);
        tenantGrid.setEmptyStateText("No hay instituciones que coincidan con los filtros.");
        tenantGrid.addColumn(Tenant::getName)
                .setHeader("Institución")
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(1);
        tenantGrid.addColumn(LitRenderer.<Tenant>of("""
                    <span class="workspace-status-badge ${item.statusTone}">${item.statusLabel}</span>
                """)
                .withProperty("statusLabel", this::adminStatusLabel)
                .withProperty("statusTone", this::adminStatusTone))
                .setHeader("Administración")
                .setAutoWidth(true)
                .setFlexGrow(0);
        tenantGrid.addColumn(LitRenderer.<Tenant>of("""
                    <vaadin-button class="workspace-row-action" theme="tertiary small" @click="${sendInvite}" aria-label="Enviar invitación a ${item.name}">
                        <vaadin-icon icon="vaadin:paperplane" slot="prefix"></vaadin-icon>
                        Enviar
                    </vaadin-button>
                """)
                .withProperty("name", Tenant::getName)
                .withFunction("sendInvite", this::openInviteDialog))
                .setHeader("Opciones")
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private Button primaryButton(String label, Runnable action) {
        var button = new Button(label, _ -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return button;
    }

    private Button secondaryButton(String label, Runnable action) {
        return new Button(label, _ -> action.run());
    }

    private void openCreateTenantDialog() {
        var dialog = new Dialog();
        UiCss.WORKSPACE_DIALOG.addTo(dialog);
        dialog.setHeaderTitle("Crear institución");

        var nameField = new TextField("Nombre");
        UiCss.WORKSPACE_FIELD.addTo(nameField);
        nameField.setPlaceholder("Ej. Facultad de Ingeniería");
        nameField.setRequiredIndicatorVisible(true);
        nameField.setWidthFull();

        var help = new Paragraph("La institución se crea sin administrador asignado. Podrás enviar la invitación desde la columna de opciones.");
        UiCss.WORKSPACE_DIALOG_COPY.addTo(help);
        dialog.add(new VerticalLayout(help, nameField));

        var cancel = secondaryButton("Cancelar", dialog::close);
        var create = primaryButton("Crear", () -> onCreateTenant(dialog, nameField));
        dialog.getFooter().add(cancel, create);
        dialog.open();
        nameField.focus();
    }

    private void openInviteDialog(Tenant tenant) {
        var dialog = new Dialog();
        UiCss.WORKSPACE_DIALOG.addTo(dialog);
        dialog.setHeaderTitle("Enviar invitación");

        var tenantName = new Span(tenant.getName());
        UiCss.WORKSPACE_DIALOG_CONTEXT.addTo(tenantName);
        var help = new Paragraph("Escribe el correo de la persona que administrará esta institución. Hasta que acepte la invitación, la institución seguirá sin administrador activo.");
        UiCss.WORKSPACE_DIALOG_COPY.addTo(help);

        var emailField = new EmailField("Correo del administrador institucional");
        UiCss.WORKSPACE_FIELD.addTo(emailField);
        emailField.setPlaceholder("nombre@institucion.edu");
        emailField.setRequiredIndicatorVisible(true);
        emailField.setErrorMessage("Escribe un correo válido.");
        emailField.setWidthFull();

        dialog.add(new VerticalLayout(tenantName, help, emailField));
        var cancel = secondaryButton("Cancelar", dialog::close);
        var send = primaryButton("Enviar invitación", () -> onInviteTenantAdmin(dialog, tenant, emailField));
        send.setIcon(new Icon(VaadinIcon.PAPERPLANE));
        dialog.getFooter().add(cancel, send);
        dialog.open();
        emailField.focus();
    }

    private void onCreateTenant(Dialog dialog, TextField tenantNameField) {
        try {
            systemAdminWorkspaceService
                    .createTenant(authenticatedAccountService.requireCurrentAccount(), tenantNameField.getValue());
            dialog.close();
            refresh();
            Notification.show("Institución creada.");
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void onInviteTenantAdmin(Dialog dialog, Tenant tenant, EmailField inviteEmailField) {
        try {
            systemAdminWorkspaceService.inviteTenantAdmin(
                authenticatedAccountService.requireCurrentAccount(),
                tenant.getId(),
                inviteEmailField.getValue());
            dialog.close();
            refresh();
            Notification.show("Invitación enviada.");
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void refresh() {
        tenantDataView = tenantGrid.setItems(systemAdminWorkspaceService.listTenants());
        tenantDataView.addFilter(this::matchesFilters);
        applyFilters();
    }

    private void applyFilters() {
        if (tenantDataView != null) {
            tenantDataView.refreshAll();
        }
    }

    private boolean matchesFilters(Tenant tenant) {
        var term = searchField.getValue() == null ? "" : searchField.getValue().trim().toLowerCase();
        var matchesSearch = term.isBlank() || tenant.getName().toLowerCase().contains(term);
        var hasAdmin = systemAdminWorkspaceService.hasTenantAdmin(tenant.getId());
        var selectedFilter = adminFilter.getValue();
        var matchesFilter = selectedFilter == AdminFilter.ALL
                || selectedFilter == AdminFilter.WITH_ADMIN && hasAdmin
                || selectedFilter == AdminFilter.WITHOUT_ADMIN && !hasAdmin;
        return matchesSearch && matchesFilter;
    }

    private String adminStatusLabel(Tenant tenant) {
        return systemAdminWorkspaceService.hasTenantAdmin(tenant.getId()) ? "Administrador activo" : "Sin administrador";
    }

    private String adminStatusTone(Tenant tenant) {
        return systemAdminWorkspaceService.hasTenantAdmin(tenant.getId()) ? "is-active" : "is-open";
    }
}
