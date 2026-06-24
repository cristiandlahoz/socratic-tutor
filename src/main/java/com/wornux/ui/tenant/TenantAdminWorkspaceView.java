package com.wornux.ui.tenant;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.wornux.services.workspace.AccessibleTenant;
import com.wornux.services.workspace.TenantAdminWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import jakarta.annotation.security.PermitAll;

@Route(value = "tenant", autoLayout = false)
@PageTitle("Tenant admin workspace")
@PermitAll
public class TenantAdminWorkspaceView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthenticatedAccountService authenticatedAccountService;
    private final WorkspaceRoutingService workspaceRoutingService;
    private final TenantAdminWorkspaceService tenantAdminWorkspaceService;
    private final ComboBox<AccessibleTenant> tenantSelector = new ComboBox<>("Tenant context");
    private final Grid<com.wornux.data.entities.academic.AcademicPeriod> periodGrid =
            new Grid<>(com.wornux.data.entities.academic.AcademicPeriod.class, false);
    private final Grid<com.wornux.data.entities.academic.Subject> subjectGrid =
            new Grid<>(com.wornux.data.entities.academic.Subject.class, false);
    private final Grid<com.wornux.data.entities.academic.GroupClass> groupClassGrid =
            new Grid<>(com.wornux.data.entities.academic.GroupClass.class, false);
    private final TextField periodCodeField = new TextField("Period code");
    private final TextField periodNameField = new TextField("Period name");
    private final com.vaadin.flow.component.datepicker.DatePicker startDateField =
            new com.vaadin.flow.component.datepicker.DatePicker("Start date");
    private final com.vaadin.flow.component.datepicker.DatePicker endDateField =
            new com.vaadin.flow.component.datepicker.DatePicker("End date");
    private final TextField subjectCodeField = new TextField("Subject code");
    private final TextField subjectNameField = new TextField("Subject name");
    private final ComboBox<com.wornux.data.entities.academic.Subject> groupSubjectSelector = new ComboBox<>("Subject");
    private final ComboBox<com.wornux.data.entities.academic.AcademicPeriod> groupPeriodSelector =
            new ComboBox<>("Academic period");
    private final TextField groupCodeField = new TextField("Class code");
    private final TextField groupNameField = new TextField("Class name");
    private final EmailField professorEmailField = new EmailField("Professor email");

    public TenantAdminWorkspaceView(
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService,
            TenantAdminWorkspaceService tenantAdminWorkspaceService) {
        this.authenticatedAccountService = authenticatedAccountService;
        this.workspaceRoutingService = workspaceRoutingService;
        this.tenantAdminWorkspaceService = tenantAdminWorkspaceService;

        addClassName("workspace-view");
        tenantSelector.setItemLabelGenerator(AccessibleTenant::tenantName);
        tenantSelector.addValueChangeListener(event -> switchTenant(event.getValue()));
        periodGrid.addColumn(com.wornux.data.entities.academic.AcademicPeriod::getCode).setHeader("Code");
        periodGrid.addColumn(com.wornux.data.entities.academic.AcademicPeriod::getName).setHeader("Name");
        subjectGrid.addColumn(com.wornux.data.entities.academic.Subject::getCode).setHeader("Code");
        subjectGrid.addColumn(com.wornux.data.entities.academic.Subject::getName).setHeader("Name");
        groupClassGrid.addColumn(com.wornux.data.entities.academic.GroupClass::getCode).setHeader("Code");
        groupClassGrid.addColumn(com.wornux.data.entities.academic.GroupClass::getName).setHeader("Name");
        groupSubjectSelector
                .setItemLabelGenerator(subject -> "%s - %s".formatted(subject.getCode(), subject.getName()));
        groupPeriodSelector.setItemLabelGenerator(period -> "%s - %s".formatted(period.getCode(), period.getName()));

        add(
            new H1("Tenant admin workspace"),
            tenantSelector,
            new HorizontalLayout(periodCodeField,
                    periodNameField,
                    startDateField,
                    endDateField,
                    new Button("Create period", _ -> onCreatePeriod())),
            periodGrid,
            new HorizontalLayout(subjectCodeField,
                    subjectNameField,
                    new Button("Create subject", _ -> onCreateSubject())),
            subjectGrid,
            new HorizontalLayout(groupSubjectSelector,
                    groupPeriodSelector,
                    groupCodeField,
                    groupNameField,
                    new Button("Create class", _ -> onCreateClass())),
            groupClassGrid,
            new HorizontalLayout(professorEmailField, new Button("Invite professor", _ -> onInviteProfessor())));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.TENANT_ADMIN)) {
            event.forwardTo("no-access");
            return;
        }
        refresh();
    }

    private void refresh() {
        var account = authenticatedAccountService.requireCurrentAccount();
        var tenants = tenantAdminWorkspaceService.listAccessibleTenants(account);
        tenantSelector.setItems(tenants);
        var selectedTenant = determineSelectedTenant(tenants, tenantSelector.getValue(), account);
        if (selectedTenant != null && tenantSelector.getValue() != selectedTenant) {
            tenantSelector.setValue(selectedTenant);
        }
        var activeTenant = tenantSelector.getValue();
        if (activeTenant == null) {
            return;
        }
        periodGrid.setItems(tenantAdminWorkspaceService.listPeriods(activeTenant.tenantId()));
        subjectGrid.setItems(tenantAdminWorkspaceService.listSubjects(activeTenant.tenantId()));
        var classes = tenantAdminWorkspaceService.listGroupClasses(activeTenant.tenantId());
        groupClassGrid.setItems(classes);
        groupSubjectSelector.setItems(tenantAdminWorkspaceService.listSubjects(activeTenant.tenantId()));
        groupPeriodSelector.setItems(tenantAdminWorkspaceService.listPeriods(activeTenant.tenantId()));
    }

    public static AccessibleTenant determineSelectedTenant(
            java.util.List<AccessibleTenant> tenants,
            AccessibleTenant currentValue,
            com.wornux.data.entities.identity.Account account) {
        if (tenants == null || tenants.isEmpty()) {
            return null;
        }
        if (currentValue != null
                && tenants.stream()
                        .anyMatch(tenant -> tenant.tenantAccountId().equals(currentValue.tenantAccountId()))) {
            return currentValue;
        }
        if (account.getLastTenantAccount() != null) {
            return tenants.stream()
                    .filter(tenant -> tenant.tenantAccountId().equals(account.getLastTenantAccount().getId()))
                    .findFirst()
                    .orElse(tenants.getFirst());
        }
        return tenants.getFirst();
    }

    private void switchTenant(AccessibleTenant tenant) {
        if (tenant == null) {
            return;
        }
        var account = authenticatedAccountService.requireCurrentAccount();
        if (account.getLastTenantAccount() != null
                && account.getLastTenantAccount().getId().equals(tenant.tenantAccountId())) {
            return;
        }
        workspaceRoutingService.switchTenant(account, tenant.tenantAccountId());
        refresh();
    }

    private void onCreatePeriod() {
        try {
            tenantAdminWorkspaceService.createPeriod(
                authenticatedAccountService.requireCurrentAccount(),
                periodCodeField.getValue(),
                periodNameField.getValue(),
                startDateField.getValue(),
                endDateField.getValue());
            refresh();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void onCreateSubject() {
        try {
            tenantAdminWorkspaceService.createSubject(
                authenticatedAccountService.requireCurrentAccount(),
                subjectCodeField.getValue(),
                subjectNameField.getValue());
            refresh();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void onCreateClass() {
        try {
            tenantAdminWorkspaceService.createGroupClass(
                authenticatedAccountService.requireCurrentAccount(),
                groupSubjectSelector.getValue().getId(),
                groupPeriodSelector.getValue().getId(),
                groupCodeField.getValue(),
                groupNameField.getValue());
            refresh();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void onInviteProfessor() {
        var selectedClass = groupClassGrid.asSingleSelect().getValue();
        if (selectedClass == null) {
            Notification.show("Select a class first.");
            return;
        }
        try {
            tenantAdminWorkspaceService.inviteProfessor(
                authenticatedAccountService.requireCurrentAccount(),
                selectedClass.getId(),
                professorEmailField.getValue());
            professorEmailField.clear();
            Notification.show("Invitation sent.");
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }
}
