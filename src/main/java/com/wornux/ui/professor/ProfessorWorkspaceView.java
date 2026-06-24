package com.wornux.ui.professor;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.workspace.AccessibleClass;
import com.wornux.services.workspace.ProfessorWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.chat.ChatView;
import com.wornux.ui.ingestion.DocumentIngestionView;
import com.wornux.ui.training_activity.TrainingActivityView;
import jakarta.annotation.security.PermitAll;

@Route(value = "professor", autoLayout = false)
@PageTitle("Professor workspace")
@PermitAll
public class ProfessorWorkspaceView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthenticatedAccountService authenticatedAccountService;
    private final WorkspaceRoutingService workspaceRoutingService;
    private final ProfessorWorkspaceService professorWorkspaceService;
    private final ComboBox<AccessibleClass> classSelector = new ComboBox<>("Class context");
    private final Grid<com.wornux.data.entities.academic.GroupClassMember> studentsGrid =
            new Grid<>(com.wornux.data.entities.academic.GroupClassMember.class, false);
    private final EmailField studentEmailField = new EmailField("Student email");

    public ProfessorWorkspaceView(
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService,
            ProfessorWorkspaceService professorWorkspaceService) {
        this.authenticatedAccountService = authenticatedAccountService;
        this.workspaceRoutingService = workspaceRoutingService;
        this.professorWorkspaceService = professorWorkspaceService;

        addClassName("workspace-view");
        classSelector.setItemLabelGenerator(value -> "%s - %s".formatted(value.classCode(), value.className()));
        classSelector.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                switchClass(event.getValue());
            }
        });
        studentsGrid.addColumn(
            member -> "%s %s".formatted(
                member.getTenantAccount().getAccount().getFirstName(),
                member.getTenantAccount().getAccount().getLastName())).setHeader("Student");
        studentsGrid.addColumn(member -> member.getTenantAccount().getAccount().getEmail()).setHeader("Email");
        studentsGrid.addColumn(member -> member.isLocked() ? "Disabled" : "Active").setHeader("Status");
        studentsGrid.addComponentColumn(member -> new Button("Disable", _ -> disableStudent(member.getId())))
                .setHeader("Actions");

        add(
            new H1("Professor workspace"),
            classSelector,
            new HorizontalLayout(new Button("Open chat", _ -> UI.getCurrent().navigate(ChatView.class)),
                    new Button("Documents", _ -> UI.getCurrent().navigate(DocumentIngestionView.class)),
                    new Button("Formative Activities", _ -> UI.getCurrent().navigate(TrainingActivityView.class))),
            new HorizontalLayout(studentEmailField, new Button("Invite student", _ -> inviteStudent())),
            studentsGrid);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR)) {
            event.forwardTo("no-access");
            return;
        }
        refresh();
    }

    private void refresh() {
        var account = authenticatedAccountService.requireCurrentAccount();
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
                .switchClass(authenticatedAccountService.requireCurrentAccount(), accessibleClass.groupClassMemberId());
        refresh();
    }

    private void inviteStudent() {
        try {
            professorWorkspaceService
                    .inviteStudent(authenticatedAccountService.requireCurrentAccount(), studentEmailField.getValue());
            studentEmailField.clear();
            Notification.show("Invitation sent.");
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void disableStudent(java.util.UUID memberId) {
        try {
            professorWorkspaceService
                    .disableStudentMembership(authenticatedAccountService.requireCurrentAccount(), memberId);
            refresh();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }
}
