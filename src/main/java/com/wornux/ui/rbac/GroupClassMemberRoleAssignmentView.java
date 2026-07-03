package com.wornux.ui.rbac;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.authorization.Role;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.authorization.RoleAdministrationService;
import com.wornux.security.permission.AppPermission;
import com.wornux.ui.MainLayout;
import jakarta.annotation.security.PermitAll;

@Route(value = "roles/class-assignments", layout = MainLayout.class)
@PageTitle("Asignación de roles de clase")
@PermitAll
@RequiresPermission(AppPermission.ROLE_ASSIGN)
public class GroupClassMemberRoleAssignmentView extends VerticalLayout {

    private final RoleAdministrationService roleAdministrationService;
    private final ComboBox<GroupClass> classSelector = new ComboBox<>("Clase");
    private final Grid<GroupClassMember> grid = new Grid<>(GroupClassMember.class, false);

    public GroupClassMemberRoleAssignmentView(RoleAdministrationService roleAdministrationService) {
        this.roleAdministrationService = roleAdministrationService;
        setSizeFull();
        configureClassSelector();
        add(new H1("Asignación de roles de clase"), new Paragraph("Asigna roles GROUP_CLASS a miembros de la clase seleccionada. Esta matriz no cambia member_kind."), classSelector, grid);
    }

    private void configureClassSelector() {
        List<GroupClass> classes = roleAdministrationService.activeTenantClasses();
        classSelector.setItems(classes);
        classSelector.setItemLabelGenerator(groupClass -> "%s · %s".formatted(groupClass.getCode(), groupClass.getName()));
        classSelector.addValueChangeListener(event -> refresh());
        if (!classes.isEmpty()) {
            classSelector.setValue(classes.getFirst());
        }
    }

    private void refresh() {
        grid.removeAllColumns();
        var selected = classSelector.getValue();
        if (selected == null) {
            grid.setItems(List.of());
            return;
        }
        var matrix = roleAdministrationService.groupClassAssignments(selected.getId());
        grid.addColumn(this::displayName).setHeader("Miembro").setAutoWidth(true);
        grid.addColumn(member -> member.getMemberKind().name()).setHeader("member_kind").setAutoWidth(true);
        for (var role : matrix.roles()) {
            grid.addComponentColumn(member -> checkbox(member.getId(), role)).setHeader(role.getName()).setAutoWidth(true);
        }
        grid.setItems(matrix.members());
    }

    private Checkbox checkbox(UUID groupClassMemberId, Role role) {
        Set<UUID> assignedRoles = roleAdministrationService.groupClassMemberRoleIds(groupClassMemberId);
        var checkbox = new Checkbox();
        checkbox.setValue(assignedRoles.contains(role.getId()));
        checkbox.setTooltipText("assignment_level=GROUP_CLASS; no edita member_kind");
        checkbox.addValueChangeListener(event -> {
            if (!event.isFromClient()) {
                return;
            }
            try {
                roleAdministrationService.setGroupClassRole(groupClassMemberId, role.getId(), event.getValue());
                Notification.show("Asignación actualizada");
            }
            catch (RuntimeException ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
                checkbox.setValue(!event.getValue());
            }
        });
        return checkbox;
    }

    private String displayName(GroupClassMember member) {
        var account = member.getTenantAccount().getAccount();
        var name = "%s %s".formatted(account.getFirstName() == null ? "" : account.getFirstName(), account.getLastName() == null ? "" : account.getLastName()).trim();
        return name.isBlank() ? account.getEmail() : "%s <%s>".formatted(name, account.getEmail());
    }
}
