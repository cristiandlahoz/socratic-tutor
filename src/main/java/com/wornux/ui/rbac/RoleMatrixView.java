package com.wornux.ui.rbac;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.authorization.Role;
import com.wornux.data.entities.authorization.ScopeLevel;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.security.authorization.ActiveContextHolder;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.authorization.RoleAdministrationService;
import com.wornux.security.authorization.RoleAdministrationService.CreateRoleCommand;
import com.wornux.security.authorization.RoleAdministrationService.UpdateRoleCommand;
import com.wornux.security.permission.AppPermission;
import com.wornux.security.permission.AppResource;
import com.wornux.ui.MainLayout;
import com.wornux.ui.css.UiCss;
import jakarta.annotation.security.PermitAll;

@Route(value = "roles", layout = MainLayout.class)
@PageTitle("Roles y permisos")
@PermitAll
@RequiresPermission(AppPermission.ROLE_VIEW)
public class RoleMatrixView extends VerticalLayout {

    private enum RoleTab {
        OVERVIEW("General"),
        PERMISSIONS("Permisos"),
        MEMBERS("Miembros");

        private final String label;

        RoleTab(String label) {
            this.label = label;
        }
    }

    private static final Map<AppResource, String> RESOURCE_LABELS = Map.ofEntries(
            Map.entry(AppResource.TENANT, "Tenant"),
            Map.entry(AppResource.ACCOUNT, "Account"),
            Map.entry(AppResource.ROLE, "Role"),
            Map.entry(AppResource.SUBJECT, "Subject"),
            Map.entry(AppResource.ACADEMIC_PERIOD, "Academic Period"),
            Map.entry(AppResource.GROUP_CLASS, "Group Class"),
            Map.entry(AppResource.GROUP_CLASS_MEMBER, "Group Class Member"),
            Map.entry(AppResource.GROUP_CLASS_JOIN_CODE, "Group Class Join Code"),
            Map.entry(AppResource.CONVERSATION, "Conversation"),
            Map.entry(AppResource.TRAINING_ACTIVITY, "Training Activity"),
            Map.entry(AppResource.TRAINING_ACTIVITY_ASSIGNMENT, "Training Activity Assignment"),
            Map.entry(AppResource.COURSE_MATERIAL, "Course Material"));

    private final RoleAdministrationService roleAdministrationService;
    private final ActiveContextHolder activeContextHolder;
    private final AuthorizationService authorizationService;
    private final TextField roleSearch = new TextField();
    private final ComboBox<ScopeLevel> levelSelector = new ComboBox<>();
    private final Grid<Role> roleGrid = new Grid<>(Role.class, false);
    private final VerticalLayout roleHeader = new VerticalLayout();
    private final Div tabContent = new Div();
    private final Tabs tabs = new Tabs();
    private List<Role> visibleRoles = List.of();
    private Map<UUID, Long> roleMemberCounts = Map.of();
    private UUID selectedRoleId;
    private RoleTab selectedTab = RoleTab.OVERVIEW;

    public RoleMatrixView(
            RoleAdministrationService roleAdministrationService,
            ActiveContextHolder activeContextHolder,
            AuthorizationService authorizationService) {
        this.roleAdministrationService = roleAdministrationService;
        this.activeContextHolder = activeContextHolder;
        this.authorizationService = authorizationService;
        configureView();
        add(createHeader(), createSplitLayout());
        refreshRoleList();
    }

    private void configureView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        UiCss.ROLE_MANAGEMENT_VIEW.addTo(this);
    }

    private Component createHeader() {
        var title = new H1("Roles y permisos");
        var description = new Paragraph("Administra roles, permisos y miembros desde una sola vista.");
        UiCss.ROLE_MANAGEMENT_DESCRIPTION.addTo(description);

        var header = new VerticalLayout(title, description);
        header.setPadding(false);
        header.setSpacing(false);
        UiCss.ROLE_MANAGEMENT_HEADER.addTo(header);
        return header;
    }

    private Component createSplitLayout() {
        var split = new SplitLayout(createRolePanel(), createEditorPanel());
        split.setSizeFull();
        split.setSplitterPosition(32);
        UiCss.ROLE_MANAGEMENT_SPLIT.addTo(split);
        return split;
    }

    private Component createRolePanel() {
        configureRoleSearch();
        configureLevelSelector();
        configureRoleList();

        var createRole = new Button("Crear rol", _ -> openCreateDialog());
        createRole.addThemeVariants(ButtonVariant.PRIMARY);
        createRole.setVisible(canCreateRoles());

        var controls = new VerticalLayout(roleSearch, levelSelector, createRole);
        controls.setPadding(false);
        controls.setSpacing(true);
        UiCss.ROLE_MANAGEMENT_CONTROLS.addTo(controls);

        var panel = new VerticalLayout(new H2("Roles"), controls, roleGrid);
        panel.setSizeFull();
        panel.setPadding(false);
        UiCss.ROLE_MANAGEMENT_ROLE_PANEL.addTo(panel);
        return panel;
    }

    private void configureRoleSearch() {
        roleSearch.setPlaceholder("Buscar roles");
        roleSearch.setClearButtonVisible(true);
        roleSearch.setValueChangeMode(ValueChangeMode.LAZY);
        roleSearch.addValueChangeListener(_ -> refreshRoleList());
        roleSearch.setWidthFull();
    }

    private void configureLevelSelector() {
        levelSelector.setLabel("Nivel");
        levelSelector.setItems(levelOptions());
        levelSelector.setValue(defaultLevel());
        levelSelector.setItemLabelGenerator(this::levelLabel);
        levelSelector.setVisible(isTenantContext());
        levelSelector.addValueChangeListener(_ -> refreshRoleList());
        levelSelector.setWidthFull();
    }

    private void configureRoleList() {
        roleGrid.removeAllColumns();
        roleGrid.setSelectionMode(Grid.SelectionMode.NONE);
        roleGrid.setAllRowsVisible(true);
        roleGrid.setWidthFull();
        roleGrid.setEmptyStateText("No hay roles para el filtro actual.");
        UiCss.ROLE_MANAGEMENT_ROLE_LIST.addTo(roleGrid);
        roleGrid.addColumn(roleIdentityRenderer())
                .setHeader("Roles")
                .setFlexGrow(1);
        roleGrid.addColumn(roleMemberCountRenderer())
                .setHeader("Miembros")
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private LitRenderer<Role> roleIdentityRenderer() {
        return LitRenderer.<Role>of("""
                    <button class="role-management-role-entry ${item.selected}" @click="${selectRole}" aria-label="Seleccionar ${item.name}">
                        <span class="role-management-role-entry-icon" aria-hidden="true"></span>
                        <span class="role-management-role-entry-name">${item.name}</span>
                    </button>
                """)
                .withProperty("name", Role::getName)
                .withProperty("selected", role -> role.getId().equals(selectedRoleId) ? "is-selected" : "")
                .withFunction("selectRole", this::selectRole);
    }

    private LitRenderer<Role> roleMemberCountRenderer() {
        return LitRenderer.<Role>of("""
                    <span class="role-management-role-count" aria-label="${item.count} miembros">
                        <span>${item.count}</span>
                        <vaadin-icon src="/icons/IconPeople.svg" aria-hidden="true"></vaadin-icon>
                    </span>
                """)
                .withProperty("count", role -> roleMemberCounts.getOrDefault(role.getId(), 0L));
    }

    private Component createEditorPanel() {
        configureTabs();
        roleHeader.setPadding(false);
        roleHeader.setSpacing(false);
        tabContent.setSizeFull();
        UiCss.ROLE_MANAGEMENT_TAB_CONTENT.addTo(tabContent);

        var panel = new VerticalLayout(roleHeader, tabs, tabContent);
        panel.setSizeFull();
        panel.setPadding(false);
        UiCss.ROLE_MANAGEMENT_EDITOR_PANEL.addTo(panel);
        return panel;
    }

    private void configureTabs() {
        tabs.removeAll();
        Arrays.stream(RoleTab.values()).map(tab -> new Tab(tab.label)).forEach(tabs::add);
        tabs.addSelectedChangeListener(_ -> selectTab(tabForSelectedIndex()));
        tabs.setWidthFull();
        UiCss.ROLE_MANAGEMENT_TABS.addTo(tabs);
    }

    private List<ScopeLevel> levelOptions() {
        return switch (currentContextLevel()) {
            case PLATFORM -> List.of(ScopeLevel.PLATFORM);
            case TENANT -> List.of(ScopeLevel.TENANT, ScopeLevel.GROUP_CLASS);
            case GROUP_CLASS -> List.of(ScopeLevel.GROUP_CLASS);
        };
    }

    private ScopeLevel defaultLevel() {
        return switch (currentContextLevel()) {
            case PLATFORM -> ScopeLevel.PLATFORM;
            case TENANT -> ScopeLevel.TENANT;
            case GROUP_CLASS -> ScopeLevel.GROUP_CLASS;
        };
    }

    private ScopeLevel currentContextLevel() {
        return activeContextHolder.current().map(context -> context.level()).orElse(ScopeLevel.TENANT);
    }

    private boolean isTenantContext() {
        return currentContextLevel() == ScopeLevel.TENANT;
    }

    private String levelLabel(ScopeLevel level) {
        return switch (level) {
            case PLATFORM -> "Plataforma";
            case TENANT -> "Institución";
            case GROUP_CLASS -> "Clase";
        };
    }

    private void refreshRoleList() {
        visibleRoles = loadVisibleRoles();
        roleMemberCounts = memberCountsFor(visibleRoles);
        selectedRoleId = selectedRole(visibleRoles).map(Role::getId).orElse(null);
        renderRoleList();
        renderSelectedRole();
    }

    private Map<UUID, Long> memberCountsFor(List<Role> roles) {
        return roles.stream().collect(Collectors.toMap(Role::getId, role -> roleAdministrationService.assignedMemberCount(role.getId())));
    }

    private List<Role> loadVisibleRoles() {
        return roleAdministrationService.rolesForActiveContext(selectedLevel()).stream()
                .filter(matchesRoleSearch())
                .toList();
    }

    private ScopeLevel selectedLevel() {
        return Objects.requireNonNullElse(levelSelector.getValue(), defaultLevel());
    }

    private Predicate<Role> matchesRoleSearch() {
        var query = normalized(roleSearch.getValue());
        if (query.isBlank()) {
            return _ -> true;
        }
        return role -> normalized(role.getName()).contains(query)
                || normalized(role.getCode()).contains(query)
                || normalized(role.getDescription()).contains(query);
    }

    private Optional<Role> selectedRole(List<Role> roles) {
        return roles.stream()
                .filter(role -> role.getId().equals(selectedRoleId))
                .findFirst()
                .or(() -> roles.stream().findFirst());
    }

    private void renderRoleList() {
        roleGrid.setItems(visibleRoles);
        roleGrid.getColumns().getFirst().setHeader("Roles - %d".formatted(visibleRoles.size()));
    }

    private void selectRole(Role role) {
        selectedRoleId = role.getId();
        renderRoleList();
        renderSelectedRole();
    }

    private void renderSelectedRole() {
        roleHeader.removeAll();
        tabContent.removeAll();
        var role = currentRole();
        if (role == null) {
            tabs.setVisible(false);
            roleHeader.add(emptyState("Selecciona o crea un rol para empezar."));
            return;
        }
        tabs.setVisible(true);
        roleHeader.add(createSelectedRoleHeader(role));
        renderTabContent(role);
    }

    private Role currentRole() {
        return visibleRoles.stream()
                .filter(role -> role.getId().equals(selectedRoleId))
                .findFirst()
                .orElse(null);
    }

    private Component createSelectedRoleHeader(Role role) {
        var title = new H2(role.getName());
        var meta = new Span("%s · prioridad %d · %s · %s".formatted(
                levelLabel(role.getAssignmentLevel()),
                role.getPriority(),
                role.isActive() ? "activo" : "inactivo",
                role.isAssignable() ? "asignable" : "no asignable"));
        UiCss.ROLE_MANAGEMENT_ROLE_META.addTo(meta);
        var layout = new VerticalLayout(title, meta);
        layout.setPadding(false);
        layout.setSpacing(false);
        UiCss.ROLE_MANAGEMENT_ROLE_HEADER.addTo(layout);
        return layout;
    }

    private void selectTab(RoleTab tab) {
        selectedTab = tab;
        var role = currentRole();
        if (role != null) {
            renderTabContent(role);
        }
    }

    private RoleTab tabForSelectedIndex() {
        return RoleTab.values()[tabs.getSelectedIndex()];
    }

    private void renderTabContent(Role role) {
        tabContent.removeAll();
        tabContent.add(switch (selectedTab) {
            case OVERVIEW -> createOverviewTab(role);
            case PERMISSIONS -> createPermissionsTab(role);
            case MEMBERS -> createMembersTab(role);
        });
    }

    private Component createOverviewTab(Role role) {
        var name = new TextField("Nombre");
        var description = new TextArea("Descripción");
        var priority = new IntegerField("Prioridad");
        var active = new Checkbox("Activo", role.isActive());
        var assignable = new Checkbox("Asignable", role.isAssignable());
        name.setValue(role.getName());
        description.setValue(nullToBlank(role.getDescription()));
        priority.setValue(role.getPriority());

        var editable = roleOverviewEditable(role);
        name.setEnabled(editable);
        description.setEnabled(editable);
        priority.setEnabled(editable);
        active.setEnabled(editable);
        assignable.setEnabled(editable);

        var form = new FormLayout(name, description, priority, active, assignable);
        form.setWidthFull();

        var save = new Button("Guardar cambios", _ -> updateRoleOverview(role, name, description, active, assignable, priority));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.setVisible(editable);

        var content = createTabLayout("General", "Define el nombre, prioridad y disponibilidad del rol.");
        content.add(form, save);
        return content;
    }

    private void updateRoleOverview(
            Role role,
            TextField name,
            TextArea description,
            Checkbox active,
            Checkbox assignable,
            IntegerField priority) {
        saveRole(new UpdateRoleCommand(
                role.getId(),
                name.getValue(),
                description.getValue(),
                active.getValue(),
                assignable.getValue(),
                currentPriority(priority, role.getPriority()),
                currentPermissions(role)));
    }

    private Component createPermissionsTab(Role role) {
        var search = new TextField();
        var groups = new VerticalLayout();
        search.setPlaceholder("Buscar permisos");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setWidthFull();
        search.addValueChangeListener(_ -> renderPermissionGroups(role, groups, search.getValue()));

        var content = createTabLayout("Permisos", "Activa permisos por recurso. Solo puedes conceder permisos que ya tienes.");
        groups.setPadding(false);
        UiCss.ROLE_MANAGEMENT_PERMISSION_ROWS.addTo(groups);
        renderPermissionGroups(role, groups, search.getValue());
        content.add(search, groups);
        return content;
    }

    private void renderPermissionGroups(Role role, VerticalLayout groups, String query) {
        groups.removeAll();
        permissionsByResource().forEach((resource, permissions) -> {
            var visible = filterPermissions(role, permissions, query);
            if (!visible.isEmpty()) {
                groups.add(createPermissionGroup(role, resource, visible));
            }
        });
        if (groups.getComponentCount() == 0) {
            groups.add(emptyState("No hay permisos que coincidan con la búsqueda."));
        }
    }

    private List<AppPermission> filterPermissions(Role role, List<AppPermission> permissions, String query) {
        var normalizedQuery = normalized(query);
        return permissions.stream()
                .filter(permission -> permissionVisibleForLevel(role, permission))
                .filter(permission -> normalizedQuery.isBlank() || permissionMatches(permission, normalizedQuery))
                .toList();
    }

    private boolean permissionVisibleForLevel(Role role, AppPermission permission) {
        return roleAdministrationService.permissionValidForLevel(permission.code(), role.getAssignmentLevel());
    }

    private boolean permissionMatches(AppPermission permission, String query) {
        return normalized(permission.code()).contains(query)
                || normalized(permission.action().name()).contains(query)
                || normalized(RESOURCE_LABELS.get(permission.resource())).contains(query);
    }

    private Component createPermissionGroup(Role role, AppResource resource, List<AppPermission> permissions) {
        var title = new H3(RESOURCE_LABELS.get(resource));
        var rows = new VerticalLayout();
        rows.setPadding(false);
        rows.setSpacing(false);
        UiCss.ROLE_MANAGEMENT_PERMISSION_ROWS.addTo(rows);
        permissions.forEach(permission -> rows.add(createPermissionRow(role, permission)));

        var group = new VerticalLayout(title, rows);
        group.setWidthFull();
        UiCss.ROLE_MANAGEMENT_PERMISSION_GROUP.addTo(group);
        return group;
    }

    private Component createPermissionRow(Role role, AppPermission permission) {
        var checkbox = new Checkbox(permissionLabel(permission));
        checkbox.setValue(currentPermissions(role).contains(permission.code()));
        checkbox.setEnabled(permissionEditable(role, permission));
        disabledPermissionReason(role, permission).ifPresent(checkbox::setTooltipText);
        checkbox.addValueChangeListener(event -> updatePermission(role, permission, event.getValue(), event.isFromClient(), checkbox));

        var code = new Span(permission.code());
        UiCss.ROLE_MANAGEMENT_PERMISSION_CODE.addTo(code);

        var row = new HorizontalLayout(checkbox, code);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        UiCss.ROLE_MANAGEMENT_PERMISSION_ROW.addTo(row);
        return row;
    }

    private String permissionLabel(AppPermission permission) {
        return permission.action().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private boolean permissionEditable(Role role, AppPermission permission) {
        return canUpdateRoles() && disabledPermissionReason(role, permission).isEmpty();
    }

    private boolean roleOverviewEditable(Role role) {
        return canUpdateRoles() && disabledPermissionReason(role, AppPermission.ROLE_UPDATE).isEmpty();
    }

    private boolean roleAssignmentEditable(Role role) {
        return canAssignRoles() && disabledPermissionReason(role, AppPermission.ROLE_ASSIGN).isEmpty();
    }

    private Optional<String> disabledPermissionReason(Role role, AppPermission permission) {
        return Optional.ofNullable(roleAdministrationService.disabledReason(role, permission));
    }

    private void updatePermission(Role role, AppPermission permission, boolean enabled, boolean fromClient, Checkbox checkbox) {
        if (!fromClient) {
            return;
        }
        var updated = new LinkedHashSet<>(currentPermissions(role));
        if (enabled) {
            updated.add(permission.code());
        }
        else {
            updated.remove(permission.code());
        }
        if (!saveRole(new UpdateRoleCommand(role.getId(), role.getName(), role.getDescription(), role.isActive(), role.isAssignable(), role.getPriority(), updated))) {
            checkbox.setValue(!enabled);
        }
    }

    private Component createMembersTab(Role role) {
        if (!canAssignRoles()) {
            return emptyState("Necesitas permiso para asignar roles.");
        }
        return switch (role.getAssignmentLevel()) {
            case PLATFORM -> emptyState("La asignación de roles de plataforma todavía no está disponible en esta vista.");
            case TENANT -> createTenantMembersTab(role);
            case GROUP_CLASS -> createClassMembersTab(role);
        };
    }

    private Component createTenantMembersTab(Role role) {
        var matrix = roleAdministrationService.tenantAssignments();
        var assignedMemberIds = roleAdministrationService.tenantAccountIdsAssignedToRole(role.getId());
        var grid = new Grid<>(TenantAccount.class, false);
        grid.addColumn(this::tenantMemberName).setHeader("Miembro").setFlexGrow(1);
        grid.addComponentColumn(member -> tenantRoleCheckbox(member, role, assignedMemberIds)).setHeader(role.getName()).setAutoWidth(true).setFlexGrow(0);
        grid.setItems(matrix.members());
        grid.setWidthFull();
        UiCss.ROLE_MANAGEMENT_MEMBERS_GRID.addTo(grid);

        var content = createTabLayout("Miembros", "Asigna este rol institucional a cuentas del tenant activo.");
        content.add(grid);
        UiCss.ROLE_MANAGEMENT_MEMBERS_CONTENT.addTo(content);
        return content;
    }

    private Checkbox tenantRoleCheckbox(TenantAccount member, Role role, Set<UUID> assignedMemberIds) {
        var checkbox = new Checkbox();
        checkbox.setValue(assignedMemberIds.contains(member.getId()));
        checkbox.setEnabled(role.isActive() && role.isAssignable() && roleAssignmentEditable(role));
        disabledPermissionReason(role, AppPermission.ROLE_ASSIGN).ifPresent(checkbox::setTooltipText);
        checkbox.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                setTenantRole(member.getId(), role.getId(), event.getValue(), checkbox);
            }
        });
        return checkbox;
    }

    private void setTenantRole(UUID tenantAccountId, UUID roleId, boolean assigned, Checkbox checkbox) {
        try {
            roleAdministrationService.setTenantRole(tenantAccountId, roleId, assigned);
            Notification.show("Asignación actualizada");
        }
        catch (RuntimeException ex) {
            checkbox.setValue(!assigned);
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }

    private Component createClassMembersTab(Role role) {
        var classSelector = new ComboBox<GroupClass>("Clase");
        var gridHolder = new Div();
        gridHolder.setWidthFull();
        var classes = classesAssignableInCurrentContext();
        classSelector.setItems(classes);
        classSelector.setItemLabelGenerator(this::classLabel);
        classSelector.setWidthFull();
        classSelector.addValueChangeListener(event -> renderClassMemberGrid(role, event.getValue(), gridHolder));
        if (!classes.isEmpty()) {
            classSelector.setValue(classes.getFirst());
        }

        var content = createTabLayout("Miembros", "Asigna este rol a miembros de una clase específica.");
        content.add(classSelector, gridHolder);
        UiCss.ROLE_MANAGEMENT_MEMBERS_CONTENT.addTo(content);
        if (classes.isEmpty()) {
            gridHolder.add(emptyState("No hay clases disponibles en el tenant activo."));
        }
        return content;
    }

    private void renderClassMemberGrid(Role role, GroupClass groupClass, Div gridHolder) {
        gridHolder.removeAll();
        if (groupClass == null) {
            gridHolder.add(emptyState("Selecciona una clase."));
            return;
        }
        var matrix = roleAdministrationService.groupClassAssignments(groupClass.getId());
        var assignedMemberIds = roleAdministrationService.groupClassMemberIdsAssignedToRole(groupClass.getId(), role.getId());
        var grid = new Grid<>(GroupClassMember.class, false);
        grid.addColumn(this::classMemberName).setHeader("Miembro").setFlexGrow(1);
        grid.addColumn(member -> member.getMemberKind().name()).setHeader("Tipo").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(member -> classRoleCheckbox(member, role, assignedMemberIds)).setHeader(role.getName()).setAutoWidth(true).setFlexGrow(0);
        grid.setItems(matrix.members());
        grid.setWidthFull();
        UiCss.ROLE_MANAGEMENT_MEMBERS_GRID.addTo(grid);
        gridHolder.add(grid);
    }

    private List<GroupClass> classesAssignableInCurrentContext() {
        var classes = roleAdministrationService.activeTenantClasses();
        return currentGroupClassId()
                .map(groupClassId -> classes.stream()
                        .filter(groupClass -> groupClass.getId().equals(groupClassId))
                        .toList())
                .orElse(classes);
    }

    private Optional<UUID> currentGroupClassId() {
        return activeContextHolder.current()
                .filter(context -> context.level() == ScopeLevel.GROUP_CLASS)
                .map(context -> context.groupClassId());
    }

    private Checkbox classRoleCheckbox(GroupClassMember member, Role role, Set<UUID> assignedMemberIds) {
        var checkbox = new Checkbox();
        checkbox.setValue(assignedMemberIds.contains(member.getId()));
        checkbox.setEnabled(role.isActive() && role.isAssignable() && roleAssignmentEditable(role));
        disabledPermissionReason(role, AppPermission.ROLE_ASSIGN).ifPresent(checkbox::setTooltipText);
        checkbox.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                setClassRole(member.getId(), role.getId(), event.getValue(), checkbox);
            }
        });
        return checkbox;
    }

    private void setClassRole(UUID groupClassMemberId, UUID roleId, boolean assigned, Checkbox checkbox) {
        try {
            roleAdministrationService.setGroupClassRole(groupClassMemberId, roleId, assigned);
            Notification.show("Asignación actualizada");
        }
        catch (RuntimeException ex) {
            checkbox.setValue(!assigned);
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }

    private VerticalLayout createTabLayout(String title, String description) {
        var heading = new H3(title);
        var helper = new Paragraph(description);
        UiCss.ROLE_MANAGEMENT_TAB_DESCRIPTION.addTo(helper);

        var layout = new VerticalLayout(heading, helper);
        layout.setPadding(false);
        layout.setWidthFull();
        UiCss.ROLE_MANAGEMENT_TAB_LAYOUT.addTo(layout);
        return layout;
    }

    private void openCreateDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Crear rol");

        var name = new TextField("Nombre");
        var description = new TextArea("Descripción");
        var level = new ComboBox<ScopeLevel>("Nivel");
        var priority = new IntegerField("Prioridad");
        name.setRequiredIndicatorVisible(true);
        level.setItems(levelOptions());
        level.setItemLabelGenerator(this::levelLabel);
        level.setValue(selectedLevel());
        priority.setValue(10);

        var form = new FormLayout(name, description, level, priority);
        dialog.add(form);
        dialog.getFooter().add(new Button("Cancelar", _ -> dialog.close()), createDialogSaveButton(dialog, name, description, level, priority));
        dialog.open();
    }

    private Button createDialogSaveButton(
            Dialog dialog,
            TextField name,
            TextArea description,
            ComboBox<ScopeLevel> level,
            IntegerField priority) {
        var save = new Button("Crear", _ -> createRole(dialog, name, description, level, priority));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        return save;
    }

    private void createRole(
            Dialog dialog,
            TextField name,
            TextArea description,
            ComboBox<ScopeLevel> level,
            IntegerField priority) {
        try {
            var saved = roleAdministrationService.createRole(new CreateRoleCommand(
                    name.getValue(), description.getValue(), level.getValue(), requiredPriority(priority), Set.of()));
            selectedRoleId = saved.getId();
            dialog.close();
            refreshRoleList();
        }
        catch (RuntimeException ex) {
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }

    private boolean saveRole(UpdateRoleCommand command) {
        try {
            roleAdministrationService.updateRole(command);
            Notification.show("Rol actualizado");
            refreshRoleList();
            return true;
        }
        catch (RuntimeException ex) {
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
            return false;
        }
    }

    private int currentPriority(IntegerField priority, int fallback) {
        return Objects.requireNonNullElse(priority.getValue(), fallback);
    }

    private int requiredPriority(IntegerField priority) {
        if (priority.getValue() == null) {
            throw new IllegalArgumentException("Priority is required");
        }
        return priority.getValue();
    }

    private Set<String> currentPermissions(Role role) {
        return Arrays.stream(role.getPermissions()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<AppResource, List<AppPermission>> permissionsByResource() {
        return Arrays.stream(AppPermission.values())
                .collect(Collectors.groupingBy(AppPermission::resource, LinkedHashMap::new, Collectors.toList()));
    }

    private String tenantMemberName(TenantAccount member) {
        var account = member.getAccount();
        var name = "%s %s".formatted(nullToBlank(account.getFirstName()), nullToBlank(account.getLastName())).trim();
        return name.isBlank() ? account.getEmail() : "%s <%s>".formatted(name, account.getEmail());
    }

    private String classMemberName(GroupClassMember member) {
        return tenantMemberName(member.getTenantAccount());
    }

    private String classLabel(GroupClass groupClass) {
        return "%s · %s".formatted(groupClass.getCode(), groupClass.getName());
    }

    private Component emptyState(String message) {
        var paragraph = new Paragraph(message);
        UiCss.ROLE_MANAGEMENT_EMPTY_STATE.addTo(paragraph);
        return paragraph;
    }

    private boolean canCreateRoles() {
        return currentContextLevel() != ScopeLevel.GROUP_CLASS && can(AppPermission.ROLE_CREATE);
    }

    private boolean canUpdateRoles() {
        return can(AppPermission.ROLE_UPDATE);
    }

    private boolean canAssignRoles() {
        return can(AppPermission.ROLE_ASSIGN);
    }

    private boolean can(AppPermission permission) {
        return authorizationService.can(permission);
    }

    private String normalized(String value) {
        return nullToBlank(value).toLowerCase(Locale.ROOT).trim();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
