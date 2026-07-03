package com.wornux.ui.tenant;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
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
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.academic.AcademicPeriod;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.Subject;
import com.wornux.data.entities.identity.Account;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.workspace.AccessibleTenant;
import com.wornux.services.workspace.TenantAdminWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.auth.NoAccessView;
import jakarta.annotation.security.PermitAll;
import com.wornux.ui.css.UiCss;

@Route(value = "tenant", layout = MainLayout.class)
@PageTitle("Espacio de administración institucional")
@PermitAll
@RequiresPermission(AppPermission.GROUP_CLASS_CREATE)
public class TenantAdminWorkspaceView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AuthenticatedAccountService authenticatedAccountService;
    private final WorkspaceRoutingService workspaceRoutingService;
    private final TenantAdminWorkspaceService tenantAdminWorkspaceService;
    private final ComboBox<AccessibleTenant> tenantSelector = new ComboBox<>("Contexto institucional");
    private final TextField searchField = new TextField("Buscar");
    private final Select<AcademicPeriod> periodFilter = new Select<>();
    private final Grid<GroupClass> groupClassGrid = new Grid<>(GroupClass.class, false);
    private GridListDataView<GroupClass> groupClassDataView;
    private List<Subject> activeSubjects = List.of();
    private List<AcademicPeriod> activePeriods = List.of();

    public TenantAdminWorkspaceView(
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService,
            TenantAdminWorkspaceService tenantAdminWorkspaceService) {
        this.authenticatedAccountService = authenticatedAccountService;
        this.workspaceRoutingService = workspaceRoutingService;
        this.tenantAdminWorkspaceService = tenantAdminWorkspaceService;

        UiCss.WORKSPACE_VIEW.addTo(this);
        configureToolbarFields();
        configureGrid();

        add(
            createHeader(
                "Espacio de administración institucional",
                "Encuentra clases, períodos y asignaturas desde una sola superficie. Cambia de institución, filtra rápido y crea lo que falte sin perder el contexto."),
            createToolbar(),
            groupClassGrid);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.TENANT_ADMIN)) {
            event.forwardTo(NoAccessView.class);
            return;
        }
        refresh();
    }

    private void configureToolbarFields() {
        tenantSelector.setItemLabelGenerator(AccessibleTenant::tenantName);
        UiCss.WORKSPACE_CONTEXT_SELECT.addTo(tenantSelector);
        tenantSelector.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                switchTenant(event.getValue());
            }
        });

        UiCss.WORKSPACE_FIELD.addTo(searchField);
        searchField.setPlaceholder("Clase, código, asignatura o período");
        searchField.setClearButtonVisible(true);
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(_ -> applyFilters());

        UiCss.WORKSPACE_FIELD.addTo(periodFilter);
        periodFilter.setLabel("Período");
        periodFilter.setEmptySelectionAllowed(true);
        periodFilter.setEmptySelectionCaption("Todos los períodos");
        periodFilter.setItemLabelGenerator(period -> period == null ? "Todos los períodos" : "%s · %s".formatted(period.getCode(), period.getName()));
        periodFilter.addValueChangeListener(_ -> applyFilters());
    }

    private void configureGrid() {
        UiCss.WORKSPACE_GRID.addTo(groupClassGrid);
        UiCss.WORKSPACE_TENANT_GRID.addTo(groupClassGrid);
        groupClassGrid.setWidthFull();
        groupClassGrid.setSelectionMode(Grid.SelectionMode.NONE);
        groupClassGrid.setEmptyStateText("No hay clases que coincidan con la búsqueda.");
        groupClassGrid.addColumn(LitRenderer.<GroupClass>of("""
                    <div class="workspace-primary-cell">
                        <span class="workspace-primary-cell-title">${item.name}</span>
                        <span class="workspace-primary-cell-meta">${item.code}</span>
                    </div>
                """)
                .withProperty("name", GroupClass::getName)
                .withProperty("code", GroupClass::getCode))
                .setHeader("Clase")
                .setComparator(GroupClass::getName)
                .setAutoWidth(true)
                .setFlexGrow(1);
        groupClassGrid.addColumn(LitRenderer.<GroupClass>of("""
                    <div class="workspace-primary-cell">
                        <span class="workspace-primary-cell-title">${item.subjectName}</span>
                        <span class="workspace-primary-cell-meta">${item.subjectCode}</span>
                    </div>
                """)
                .withProperty("subjectName", this::subjectName)
                .withProperty("subjectCode", this::subjectCode))
                .setHeader("Asignatura")
                .setComparator(this::subjectName)
                .setAutoWidth(true)
                .setFlexGrow(1);
        groupClassGrid.addColumn(LitRenderer.<GroupClass>of("""
                    <div class="workspace-primary-cell">
                        <span class="workspace-primary-cell-title">${item.periodName}</span>
                        <span class="workspace-primary-cell-meta">${item.periodRange}</span>
                    </div>
                """)
                .withProperty("periodName", this::periodName)
                .withProperty("periodRange", this::periodRange))
                .setHeader("Período")
                .setComparator(this::periodName)
                .setAutoWidth(true)
                .setFlexGrow(1);
        groupClassGrid.addColumn(LitRenderer.<GroupClass>of("""
                    <span class="workspace-status-badge ${item.statusTone}">${item.statusLabel}</span>
                """)
                .withProperty("statusLabel", groupClass -> groupClass.isActive() ? "Activa" : "Inactiva")
                .withProperty("statusTone", groupClass -> groupClass.isActive() ? "is-active" : "is-open"))
                .setHeader("Estado")
                .setAutoWidth(true)
                .setFlexGrow(0);
        groupClassGrid.addColumn(LitRenderer.<GroupClass>of("""
                    <vaadin-button class="workspace-row-action" theme="tertiary small" @click="${inviteProfessor}" aria-label="Invitar profesor a ${item.name}">
                        <vaadin-icon icon="vaadin:paperplane" slot="prefix"></vaadin-icon>
                        Invitar profesor
                    </vaadin-button>
                """)
                .withProperty("name", GroupClass::getName)
                .withFunction("inviteProfessor", this::openInviteProfessorDialog))
                .setHeader("Opciones")
                .setAutoWidth(true)
                .setFlexGrow(0);
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
        var createPeriod = secondaryButton("Crear período", this::openCreatePeriodDialog);
        createPeriod.setIcon(new Icon(VaadinIcon.CALENDAR));
        var createSubject = secondaryButton("Crear asignatura", this::openCreateSubjectDialog);
        createSubject.setIcon(new Icon(VaadinIcon.BOOK));
        var createClass = primaryButton("Crear clase", this::openCreateClassDialog);
        createClass.setIcon(new Icon(VaadinIcon.PLUS));

        var toolbar = new HorizontalLayout(tenantSelector, searchField, periodFilter, createPeriod, createSubject, createClass);
        UiCss.WORKSPACE_GRID_TOOLBAR.addTo(toolbar);
        UiCss.WORKSPACE_TENANT_ADMIN_TOOLBAR.addTo(toolbar);
        toolbar.setPadding(false);
        toolbar.setMargin(false);
        toolbar.setSpacing(false);
        return toolbar;
    }

    private Button primaryButton(String label, Runnable action) {
        var button = new Button(label, _ -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return button;
    }

    private Button secondaryButton(String label, Runnable action) {
        return new Button(label, _ -> action.run());
    }

    private void openCreatePeriodDialog() {
        var dialog = new Dialog();
        UiCss.WORKSPACE_DIALOG.addTo(dialog);
        dialog.setHeaderTitle("Crear período académico");

        var code = new TextField("Código");
        code.setPlaceholder("2026-1");
        var name = new TextField("Nombre");
        name.setPlaceholder("Semestre 2026-1");
        var startsAt = new DatePicker("Fecha de inicio");
        var endsAt = new DatePicker("Fecha de cierre");
        addWorkspaceFieldClasses(code, name, startsAt, endsAt);

        var help = new Paragraph("Los períodos ordenan las clases y ayudan a encontrar rápido la oferta activa de la institución.");
        UiCss.WORKSPACE_DIALOG_COPY.addTo(help);
        dialog.add(new VerticalLayout(help, formRow(code, name), formRow(startsAt, endsAt)));
        dialog.getFooter().add(secondaryButton("Cancelar", dialog::close), primaryButton("Crear", () -> onCreatePeriod(dialog, code, name, startsAt, endsAt)));
        dialog.open();
        code.focus();
    }

    private void openCreateSubjectDialog() {
        var dialog = new Dialog();
        UiCss.WORKSPACE_DIALOG.addTo(dialog);
        dialog.setHeaderTitle("Crear asignatura");

        var code = new TextField("Código");
        code.setPlaceholder("ICC-101");
        var name = new TextField("Nombre");
        name.setPlaceholder("Introducción a la algoritmia");
        addWorkspaceFieldClasses(code, name);

        var help = new Paragraph("Crea la asignatura una vez y reutilízala al abrir nuevas clases en distintos períodos.");
        UiCss.WORKSPACE_DIALOG_COPY.addTo(help);
        dialog.add(new VerticalLayout(help, formRow(code, name)));
        dialog.getFooter().add(secondaryButton("Cancelar", dialog::close), primaryButton("Crear", () -> onCreateSubject(dialog, code, name)));
        dialog.open();
        code.focus();
    }

    private void openCreateClassDialog() {
        var dialog = new Dialog();
        UiCss.WORKSPACE_DIALOG.addTo(dialog);
        dialog.setHeaderTitle("Crear clase");

        var subject = new ComboBox<Subject>("Asignatura");
        subject.setItems(activeSubjects);
        subject.setItemLabelGenerator(value -> "%s · %s".formatted(value.getCode(), value.getName()));
        var period = new ComboBox<AcademicPeriod>("Período académico");
        period.setItems(activePeriods);
        period.setItemLabelGenerator(value -> "%s · %s".formatted(value.getCode(), value.getName()));
        var code = new TextField("Código de la clase");
        code.setPlaceholder("ICC-101-A");
        var name = new TextField("Nombre de la clase");
        name.setPlaceholder("Algoritmia · Sección A");
        addWorkspaceFieldClasses(subject, period, code, name);

        var help = new Paragraph("La clase conecta una asignatura con un período. Después podrás invitar al profesor desde la fila creada.");
        UiCss.WORKSPACE_DIALOG_COPY.addTo(help);
        dialog.add(new VerticalLayout(help, formRow(subject, period), formRow(code, name)));
        dialog.getFooter().add(secondaryButton("Cancelar", dialog::close), primaryButton("Crear", () -> onCreateClass(dialog, subject, period, code, name)));
        dialog.open();
        subject.focus();
    }

    private void openInviteProfessorDialog(GroupClass groupClass) {
        var dialog = new Dialog();
        UiCss.WORKSPACE_DIALOG.addTo(dialog);
        dialog.setHeaderTitle("Invitar profesor");

        var className = new Span("%s · %s".formatted(groupClass.getCode(), groupClass.getName()));
        UiCss.WORKSPACE_DIALOG_CONTEXT.addTo(className);
        var help = new Paragraph("Escribe el correo de la persona que tendrá acceso docente a esta clase.");
        UiCss.WORKSPACE_DIALOG_COPY.addTo(help);
        var email = new EmailField("Correo del profesor");
        email.setPlaceholder("profesor@institucion.edu");
        email.setRequiredIndicatorVisible(true);
        email.setErrorMessage("Escribe un correo válido.");
        email.setWidthFull();
        UiCss.WORKSPACE_FIELD.addTo(email);

        var send = primaryButton("Enviar invitación", () -> onInviteProfessor(dialog, groupClass, email));
        send.setIcon(new Icon(VaadinIcon.PAPERPLANE));
        dialog.add(new VerticalLayout(className, help, email));
        dialog.getFooter().add(secondaryButton("Cancelar", dialog::close), send);
        dialog.open();
        email.focus();
    }

    private HorizontalLayout formRow(Component... children) {
        var row = new HorizontalLayout(children);
        UiCss.WORKSPACE_FORM_ROW.addTo(row);
        row.setPadding(false);
        row.setMargin(false);
        row.setSpacing(false);
        return row;
    }

    private void addWorkspaceFieldClasses(Component... fields) {
        for (var field : fields) {
            UiCss.WORKSPACE_FIELD.addTo(field);
        }
    }

    private void refresh() {
        var account = authenticatedAccountService.requireCurrentAccount();
        var tenants = tenantAdminWorkspaceService.listAccessibleTenants(account);
        tenantSelector.setItems(tenants);
        var selectedTenant = determineSelectedTenant(tenants, tenantSelector.getValue(), account);
        if (selectedTenant != null && !Objects.equals(tenantSelector.getValue(), selectedTenant)) {
            tenantSelector.setValue(selectedTenant);
        }
        var activeTenant = tenantSelector.getValue();
        if (activeTenant == null) {
            activeSubjects = List.of();
            activePeriods = List.of();
            groupClassDataView = groupClassGrid.setItems(List.of());
            return;
        }
        activeSubjects = tenantAdminWorkspaceService.listSubjects(activeTenant.tenantId());
        activePeriods = tenantAdminWorkspaceService.listPeriods(activeTenant.tenantId());
        periodFilter.setItems(activePeriods);
        var classes = tenantAdminWorkspaceService.listGroupClasses(activeTenant.tenantId());
        groupClassDataView = groupClassGrid.setItems(classes);
        groupClassDataView.addFilter(this::matchesFilters);
        applyFilters();
    }

    public static AccessibleTenant determineSelectedTenant(
            java.util.List<AccessibleTenant> tenants,
            AccessibleTenant currentValue,
            Account account) {
        if (tenants == null || tenants.isEmpty()) {
            return null;
        }
        if (currentValue != null) {
            return tenants.stream()
                    .filter(tenant -> tenant.tenantAccountId().equals(currentValue.tenantAccountId()))
                    .findFirst()
                    .orElseGet(tenants::getFirst);
        }
        return tenants.getFirst();
    }

    private void switchTenant(AccessibleTenant tenant) {
        if (tenant == null) {
            return;
        }
        var account = authenticatedAccountService.requireCurrentAccount();
        workspaceRoutingService.switchTenant(account, tenant.tenantAccountId());
        refresh();
    }

    private void onCreatePeriod(Dialog dialog, TextField code, TextField name, DatePicker startsAt, DatePicker endsAt) {
        try {
            tenantAdminWorkspaceService.createPeriod(
                authenticatedAccountService.requireCurrentAccount(),
                code.getValue(),
                name.getValue(),
                startsAt.getValue(),
                endsAt.getValue());
            dialog.close();
            refresh();
            Notification.show("Período creado.");
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void onCreateSubject(Dialog dialog, TextField code, TextField name) {
        try {
            tenantAdminWorkspaceService.createSubject(
                authenticatedAccountService.requireCurrentAccount(),
                code.getValue(),
                name.getValue());
            dialog.close();
            refresh();
            Notification.show("Asignatura creada.");
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void onCreateClass(
            Dialog dialog,
            ComboBox<Subject> subject,
            ComboBox<AcademicPeriod> period,
            TextField code,
            TextField name) {
        try {
            tenantAdminWorkspaceService.createGroupClass(
                authenticatedAccountService.requireCurrentAccount(),
                subject.getValue().getId(),
                period.getValue().getId(),
                code.getValue(),
                name.getValue());
            dialog.close();
            refresh();
            Notification.show("Clase creada.");
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void onInviteProfessor(Dialog dialog, GroupClass groupClass, EmailField professorEmailField) {
        try {
            tenantAdminWorkspaceService.inviteProfessor(
                authenticatedAccountService.requireCurrentAccount(),
                groupClass.getId(),
                professorEmailField.getValue());
            dialog.close();
            refresh();
            Notification.show("Invitación enviada.");
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void applyFilters() {
        if (groupClassDataView != null) {
            groupClassDataView.refreshAll();
        }
    }

    private boolean matchesFilters(GroupClass groupClass) {
        var term = searchField.getValue() == null ? "" : searchField.getValue().trim().toLowerCase();
        var selectedPeriod = periodFilter.getValue();
        var matchesPeriod = selectedPeriod == null || groupClass.getAcademicPeriod().getId().equals(selectedPeriod.getId());
        if (term.isBlank()) {
            return matchesPeriod;
        }
        return matchesPeriod
                && (contains(groupClass.getCode(), term)
                        || contains(groupClass.getName(), term)
                        || contains(subjectCode(groupClass), term)
                        || contains(subjectName(groupClass), term)
                        || contains(periodName(groupClass), term));
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase().contains(term);
    }

    private String subjectCode(GroupClass groupClass) {
        return groupClass.getSubject().getCode();
    }

    private String subjectName(GroupClass groupClass) {
        return groupClass.getSubject().getName();
    }

    private String periodName(GroupClass groupClass) {
        return "%s · %s".formatted(groupClass.getAcademicPeriod().getCode(), groupClass.getAcademicPeriod().getName());
    }

    private String periodRange(GroupClass groupClass) {
        var period = groupClass.getAcademicPeriod();
        return "%s — %s".formatted(DATE_FORMAT.format(period.getStartsAt()), DATE_FORMAT.format(period.getEndsAt()));
    }
}
