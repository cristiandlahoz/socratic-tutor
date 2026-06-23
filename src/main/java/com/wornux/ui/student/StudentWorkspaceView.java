package com.wornux.ui.student;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.workspace.AccessibleClass;
import com.wornux.services.workspace.StudentWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.chat.ChatView;

@Route(value = "student", autoLayout = false)
@PageTitle("Student workspace")
@PermitAll
public class StudentWorkspaceView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthenticatedAccountService authenticatedAccountService;
    private final WorkspaceRoutingService workspaceRoutingService;
    private final StudentWorkspaceService studentWorkspaceService;
    private final ComboBox<AccessibleClass> classSelector = new ComboBox<>("Class context");
    private final Grid<com.wornux.data.entities.evaluation.EvaluationAssignment> assignmentsGrid = new Grid<>(com.wornux.data.entities.evaluation.EvaluationAssignment.class, false);

    public StudentWorkspaceView(
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService,
            StudentWorkspaceService studentWorkspaceService) {
        this.authenticatedAccountService = authenticatedAccountService;
        this.workspaceRoutingService = workspaceRoutingService;
        this.studentWorkspaceService = studentWorkspaceService;

        addClassName("workspace-view");
        classSelector.setItemLabelGenerator(value -> value.classCode() + " - " + value.className());
        classSelector.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                switchClass(event.getValue());
            }
        });
        assignmentsGrid.addColumn(assignment -> assignment.getEvaluation().getTitle()).setHeader("Assigned Activity");
        assignmentsGrid.addColumn(assignment -> assignment.getStatus().name()).setHeader("Status");

        add(new H1("Student workspace"), classSelector, new Button("Open chat", _ -> UI.getCurrent().navigate(ChatView.class)), assignmentsGrid);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.STUDENT)) {
            event.forwardTo("no-access");
            return;
        }
        refresh();
    }

    private void refresh() {
        var account = authenticatedAccountService.requireCurrentAccount();
        var classes = studentWorkspaceService.listStudentClasses(account);
        classSelector.setItems(classes);
        if (!classes.isEmpty() && classSelector.getValue() == null) {
            classSelector.setValue(classes.getFirst());
        }
        assignmentsGrid.setItems(studentWorkspaceService.listAssignments(account));
    }

    private void switchClass(AccessibleClass accessibleClass) {
        if (accessibleClass == null) {
            return;
        }
        studentWorkspaceService.switchClass(authenticatedAccountService.requireCurrentAccount(), accessibleClass.groupClassMemberId());
        refresh();
    }
}
