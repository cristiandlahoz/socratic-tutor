package com.wornux.ui.rbac;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.authorization.Role;
import com.wornux.data.entities.authorization.RoleAssignmentLevel;
import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.security.authorization.ActiveContextHolder;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.authorization.RoleAdministrationService;
import com.wornux.security.authorization.RoleAdministrationService.CreateRoleCommand;
import com.wornux.security.authorization.RoleAdministrationService.UpdateRoleCommand;
import com.wornux.security.permission.AppPermission;
import com.wornux.security.permission.AppResource;
import com.wornux.ui.MainLayout;
import jakarta.annotation.security.PermitAll;

@Route(value = "roles", layout = MainLayout.class)
@PageTitle("Matriz de roles")
@PermitAll
@RequiresPermission(AppPermission.ROLE_VIEW)
public class RoleMatrixView extends VerticalLayout {

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
    private final ComboBox<RoleAssignmentLevel> levelSelector = new ComboBox<>("Tipo de rol visible");
    private final VerticalLayout matrix = new VerticalLayout();

    public RoleMatrixView(
            RoleAdministrationService roleAdministrationService,
            ActiveContextHolder activeContextHolder) {
        this.roleAdministrationService = roleAdministrationService;
        this.activeContextHolder = activeContextHolder;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        var title = new H1("Matriz de roles");
        var description = new Paragraph(
                "Administra paquetes de permisos. La membresía académica y la propiedad de datos se gestionan fuera de RBAC.");
        var create = new Button("Crear rol", _ -> openCreateDialog());
        create.addThemeVariants(ButtonVariant.PRIMARY);
        levelSelector.setItems(RoleAssignmentLevel.TENANT, RoleAssignmentLevel.GROUP_CLASS);
        levelSelector.setValue(RoleAssignmentLevel.TENANT);
        levelSelector.addValueChangeListener(_ -> refresh());
        matrix.setPadding(false);
        matrix.setWidthFull();

        add(title, description, new HorizontalLayout(levelSelector, create), matrix);
        refresh();
    }

    private void refresh() {
        matrix.removeAll();
        var context = activeContextHolder.current().orElse(null);
        if (context == null) {
            matrix.add(new Paragraph("Selecciona un contexto antes de administrar roles."));
            return;
        }
        if (context.level() == ContextLevel.GROUP_CLASS) {
            matrix.add(
                new Paragraph(
                        "Los roles se crean y editan desde la administración institucional. Vuelve al contexto de tenant si tienes acceso."));
            return;
        }
        levelSelector.setVisible(context.level() == ContextLevel.TENANT);
        var visibleLevel =
                context.level() == ContextLevel.PLATFORM ? RoleAssignmentLevel.PLATFORM : levelSelector.getValue();
        roleAdministrationService.rolesForActiveContext(visibleLevel).forEach(role -> matrix.add(roleCard(role)));
    }

    private Component roleCard(Role role) {
        var card = new VerticalLayout();
        card.setWidthFull();
        card.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        card.getStyle().set("border-radius", "12px");
        card.getStyle().set("padding", "1rem");
        var heading = new H2("%s · %s".formatted(role.getName(), role.getAssignmentLevel()));
        var meta = new Span("Prioridad %d · %s · %s".formatted(
            role.getPriority(),
            role.isActive() ? "activo" : "inactivo",
            role.isAssignable() ? "asignable" : "no asignable"));
        var edit = new Button("Editar", _ -> openEditDialog(role));
        card.add(new HorizontalLayout(heading, edit), meta);
        permissionsByResource()
                .forEach((resource, permissions) -> card.add(permissionGroup(role, resource, permissions)));
        return card;
    }

    private Details permissionGroup(Role role, AppResource resource, List<AppPermission> permissions) {
        var content = new VerticalLayout();
        content.setPadding(false);
        var current = Set.of(role.getPermissions());
        for (var permission : permissions) {
            var checkbox = new Checkbox(permission.action().name().toLowerCase());
            checkbox.setValue(current.contains(permission.code()));
            var reason = roleAdministrationService.disabledReason(role, permission);
            checkbox.setEnabled(reason == null);
            if (reason != null) {
                checkbox.setTooltipText(reason);
            }
            checkbox.addValueChangeListener(event -> {
                if (!event.isFromClient()) {
                    return;
                }
                var updated = new LinkedHashSet<>(currentPermissions(role));
                if (event.getValue()) {
                    updated.add(permission.code());
                }
                else {
                    updated.remove(permission.code());
                }
                try {
                    roleAdministrationService.updateRole(
                        new UpdateRoleCommand(role.getId(),
                                role.getName(),
                                role.getDescription(),
                                role.isActive(),
                                role.isAssignable(),
                                role.getPriority(),
                                updated));
                    Notification.show("Rol actualizado");
                    refresh();
                }
                catch (RuntimeException ex) {
                    Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
                    checkbox.setValue(!event.getValue());
                }
            });
            content.add(checkbox);
        }
        return new Details(RESOURCE_LABELS.get(resource), content);
    }

    private void openCreateDialog() {
        var context = activeContextHolder.current().orElse(null);
        if (context != null && context.level() == ContextLevel.GROUP_CLASS) {
            Notification.show("Crea roles desde el contexto institucional.");
            return;
        }
        var dialog = new Dialog();
        dialog.setHeaderTitle("Crear rol");
        var name = new TextField("Nombre");
        name.setRequiredIndicatorVisible(true);
        var description = new TextArea("Descripción");
        var level = new ComboBox<RoleAssignmentLevel>("Nivel de asignación");
        if (context != null && context.level() == ContextLevel.PLATFORM) {
            level.setItems(RoleAssignmentLevel.PLATFORM);
            level.setValue(RoleAssignmentLevel.PLATFORM);
        }
        else {
            level.setItems(RoleAssignmentLevel.TENANT, RoleAssignmentLevel.GROUP_CLASS);
            level.setValue(levelSelector.getValue());
        }
        var priority = new IntegerField("Prioridad");
        priority.setValue(10);
        var permissions = new CheckboxGroup<String>("Permisos");
        permissions.setItems(permissionCodesFor(level.getValue()));
        level.addValueChangeListener(event -> permissions.setItems(permissionCodesFor(event.getValue())));
        var form = new FormLayout(name, description, level, priority, permissions);
        dialog.add(form);
        var save = new Button("Crear", _ -> {
            try {
                roleAdministrationService.createRole(
                    new CreateRoleCommand(name.getValue(),
                            description.getValue(),
                            level.getValue(),
                            priority.getValue(),
                            permissions.getValue()));
                dialog.close();
                refresh();
            }
            catch (RuntimeException ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        dialog.getFooter().add(new Button("Cancelar", _ -> dialog.close()), save);
        dialog.open();
    }

    private void openEditDialog(Role role) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Editar rol");
        var name = new TextField("Nombre", role.getName());
        var description = new TextArea("Descripción", role.getDescription() == null ? "" : role.getDescription());
        var active = new Checkbox("Activo", role.isActive());
        var assignable = new Checkbox("Asignable", role.isAssignable());
        var priority = new IntegerField("Prioridad");
        priority.setValue(role.getPriority());
        var permissions = new CheckboxGroup<String>("Permisos");
        permissions.setItems(permissionCodesFor(role.getAssignmentLevel()));
        permissions.setValue(currentPermissions(role));
        dialog.add(new FormLayout(name, description, active, assignable, priority, permissions));
        var save = new Button("Guardar", _ -> {
            try {
                roleAdministrationService.updateRole(
                    new UpdateRoleCommand(role.getId(),
                            name.getValue(),
                            description.getValue(),
                            active.getValue(),
                            assignable.getValue(),
                            priority.getValue(),
                            permissions.getValue()));
                dialog.close();
                refresh();
            }
            catch (RuntimeException ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        dialog.getFooter().add(new Button("Cancelar", _ -> dialog.close()), save);
        dialog.open();
    }

    private Set<String> currentPermissions(Role role) {
        return Arrays.stream(role.getPermissions()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> permissionCodesFor(RoleAssignmentLevel level) {
        return Arrays.stream(AppPermission.values())
                .filter(permission -> roleAdministrationService.permissionValidForLevel(permission.code(), level))
                .map(AppPermission::code)
                .toList();
    }

    private Map<AppResource, List<AppPermission>> permissionsByResource() {
        return Arrays.stream(AppPermission.values())
                .collect(
                    Collectors.groupingBy(AppPermission::resource, java.util.LinkedHashMap::new, Collectors.toList()));
    }
}
