package com.wornux.ui.professor;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.workspace.ProfessorWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.ui.MainLayout;
import com.wornux.ui.components.TerminalDialog;
import com.wornux.ui.components.WorkspaceViewShell;
import com.wornux.ui.css.UiCss;
import jakarta.annotation.security.PermitAll;

@Route(value = "professor", layout = MainLayout.class)
@PageTitle("Espacio del profesor")
@PermitAll
@RequiresPermission(value = AppPermission.GROUP_CLASS_MEMBER_VIEW, workspace = WorkspaceDestination.PROFESSOR)
public class ProfessorWorkspaceView extends WorkspaceViewShell implements AfterNavigationObserver {

    private final AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final ProfessorWorkspaceService professorWorkspaceService;
    private final Grid<com.wornux.data.entities.academic.GroupClassMember> studentsGrid =
            new Grid<>(GroupClassMember.class, false);

    public ProfessorWorkspaceView(
            AuthenticatedUserContextUtils authenticatedUserContextUtils,
            ProfessorWorkspaceService professorWorkspaceService) {
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
        this.professorWorkspaceService = professorWorkspaceService;

        UiCss.WORKSPACE_GRID.addTo(studentsGrid);
        UiCss.WORKSPACE_TENANT_GRID.addTo(studentsGrid);
        studentsGrid.setWidthFull();
        studentsGrid.setSelectionMode(Grid.SelectionMode.NONE);
        studentsGrid.addColumn(
            member -> "%s %s".formatted(
                member.getTenantAccount().getAccount().getFirstName(),
                member.getTenantAccount().getAccount().getLastName())).setHeader("Estudiante");
        studentsGrid.addColumn(member -> member.getTenantAccount().getAccount().getEmail()).setHeader("Correo");
        studentsGrid.addColumn(member -> member.isLocked() ? "Deshabilitado" : "Activo").setHeader("Estado");
        studentsGrid.addComponentColumn(member -> new Button("Deshabilitar", _ -> disableStudent(member.getId())))
                .setHeader("Acciones");

        var inviteButton = primaryButton("Enviar invitación", this::openInviteStudentDialog);
        inviteButton.setIcon(new SvgIcon("/icons/IconEnvelope.svg"));
        var actionBar = toolbar(inviteButton);
        UiCss.PROFESSOR_ACTION_BAR.addTo(actionBar);

        setWorkspaceContent(
            "Espacio del profesor",
            "Revisa quién tiene acceso a tu clase activa y envía invitaciones puntuales desde un flujo enfocado.",
            actionBar,
            studentsGrid);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        refresh();
    }

    private void refresh() {
        var account = authenticatedUserContextUtils.requireCurrentAccount();
        studentsGrid.setItems(professorWorkspaceService.listStudents(account));
    }

    private void openInviteStudentDialog() {
        var emailField = new EmailField("Correo del estudiante");
        UiCss.WORKSPACE_FIELD.addTo(emailField);
        emailField.setPlaceholder("estudiante@institucion.edu");
        emailField.setErrorMessage("Escribe un correo válido.");
        emailField.setWidthFull();
        emailField.getElement().setAttribute("autocomplete", "off");
        emailField.getElement().setAttribute("autocapitalize", "none");
        emailField.getElement().setAttribute("autocorrect", "off");
        emailField.getElement().setAttribute("spellcheck", "false");
        emailField.getElement().setAttribute("data-1p-ignore", "true");
        emailField.getElement().setAttribute("data-lpignore", "true");
        emailField.getElement().setAttribute("data-form-type", "other");

        var dialog = new TerminalDialog(
            "student.invitation",
            "Invitar estudiante",
            "Escribe el correo institucional del estudiante. Recibirá un enlace para unirse a la clase activa.",
            emailField);
        var cancel = secondaryButton("Cancelar", dialog::close);
        var send = primaryButton("Enviar invitación", () -> inviteStudent(dialog, emailField));
        send.setIcon(new SvgIcon("/icons/IconEnvelope.svg"));
        dialog.addActions(cancel, send);
        dialog.open();
        emailField.getElement().executeJs("""
                const input = this.shadowRoot?.querySelector('input');
                if (input) {
                  input.setAttribute('autocomplete', 'off');
                  input.setAttribute('autocapitalize', 'none');
                  input.setAttribute('autocorrect', 'off');
                  input.setAttribute('spellcheck', 'false');
                  input.setAttribute('data-1p-ignore', 'true');
                  input.setAttribute('data-lpignore', 'true');
                  input.setAttribute('data-form-type', 'other');
                }
                """);
        emailField.focus();
    }

    private void inviteStudent(Dialog dialog, EmailField inviteEmailField) {
        if (inviteEmailField.isEmpty()) {
            inviteEmailField.setInvalid(true);
            return;
        }
        try {
            professorWorkspaceService
                    .inviteStudent(authenticatedUserContextUtils.requireCurrentAccount(), inviteEmailField.getValue());
            dialog.close();
            refresh();
            Notification.show("Invitación enviada.");
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void disableStudent(java.util.UUID memberId) {
        try {
            professorWorkspaceService
                    .disableStudentMembership(authenticatedUserContextUtils.requireCurrentAccount(), memberId);
            refresh();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }
}
