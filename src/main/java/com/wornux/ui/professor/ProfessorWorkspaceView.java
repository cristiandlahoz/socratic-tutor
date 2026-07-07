package com.wornux.ui.professor;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.workspace.AccessibleClass;
import com.wornux.services.workspace.ProfessorWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.auth.NoAccessView;
import com.wornux.ui.conversation.ConversationView;
import com.wornux.ui.css.UiCss;
import com.wornux.ui.ingestion.DocumentIngestionView;
import com.wornux.ui.training_activity.TrainingActivityView;
import jakarta.annotation.security.PermitAll;

@Route(value = "professor", layout = MainLayout.class)
@PageTitle("Espacio del profesor")
@PermitAll
@RequiresPermission(AppPermission.GROUP_CLASS_MEMBER_VIEW)
public class ProfessorWorkspaceView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final WorkspaceRoutingService workspaceRoutingService;
    private final ProfessorWorkspaceService professorWorkspaceService;
    private final ComboBox<AccessibleClass> classSelector = new ComboBox<>("Contexto de clase");
    private final Grid<com.wornux.data.entities.academic.GroupClassMember> studentsGrid =
            new Grid<>(GroupClassMember.class, false);
    private final EmailField studentEmailField = new EmailField("Correo del estudiante");

    public ProfessorWorkspaceView(
            AuthenticatedUserContextUtils authenticatedUserContextUtils,
            WorkspaceRoutingService workspaceRoutingService,
            ProfessorWorkspaceService professorWorkspaceService) {
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
        this.workspaceRoutingService = workspaceRoutingService;
        this.professorWorkspaceService = professorWorkspaceService;

        UiCss.WORKSPACE_VIEW.addTo(this);
        classSelector.setItemLabelGenerator(value -> "%s - %s".formatted(value.classCode(), value.className()));
        classSelector.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                switchClass(event.getValue());
            }
        });
        studentsGrid.addColumn(
            member -> "%s %s".formatted(
                member.getTenantAccount().getAccount().getFirstName(),
                member.getTenantAccount().getAccount().getLastName())).setHeader("Estudiante");
        studentsGrid.addColumn(member -> member.getTenantAccount().getAccount().getEmail()).setHeader("Correo");
        studentsGrid.addColumn(member -> member.isLocked() ? "Deshabilitado" : "Activo").setHeader("Estado");
        studentsGrid.addComponentColumn(member -> new Button("Deshabilitar", _ -> disableStudent(member.getId())))
                .setHeader("Acciones");

        add(
            new H1("Espacio del profesor"),
            classSelector,
            new HorizontalLayout(
                    new Button("Abrir conversación", _ -> UI.getCurrent().navigate(ConversationView.class)),
                    new Button("Documentos", _ -> UI.getCurrent().navigate(DocumentIngestionView.class)),
                    new Button("Actividades formativas", _ -> UI.getCurrent().navigate(TrainingActivityView.class))),
            new HorizontalLayout(studentEmailField,
                    new Button("Enviar invitación", new SvgIcon("/icons/IconEnvelope.svg"), _ -> inviteStudent())),
            studentsGrid);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedUserContextUtils.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR)) {
            event.forwardTo(NoAccessView.class);
            return;
        }
        refresh();
    }

    private void refresh() {
        var account = authenticatedUserContextUtils.requireCurrentAccount();
        var classes = professorWorkspaceService.listProfessorClasses(account);
        classSelector.setItems(classes);
        if (!classes.isEmpty() && classSelector.getValue() == null) {
            classSelector.setValue(classes.getFirst());
        }
        studentsGrid.setItems(professorWorkspaceService.listStudents(account));
    }

    private void switchClass(AccessibleClass accessibleClass) {
        if (accessibleClass == null) {
            return;
        }
        professorWorkspaceService
                .switchClass(authenticatedUserContextUtils.requireCurrentAccount(), accessibleClass.groupClassMemberId());
        refresh();
    }

    private void inviteStudent() {
        try {
            professorWorkspaceService
                    .inviteStudent(authenticatedUserContextUtils.requireCurrentAccount(), studentEmailField.getValue());
            studentEmailField.clear();
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
