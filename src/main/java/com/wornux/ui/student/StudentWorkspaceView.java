package com.wornux.ui.student;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.workspace.AccessibleClass;
import com.wornux.services.workspace.StudentWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.conversation.ConversationView;
import jakarta.annotation.security.PermitAll;

@Route(value = "student", layout = MainLayout.class)
@PageTitle("Espacio del estudiante")
@PermitAll
public class StudentWorkspaceView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthenticatedAccountService authenticatedAccountService;
    private final WorkspaceRoutingService workspaceRoutingService;
    private final StudentWorkspaceService studentWorkspaceService;
    private final ComboBox<AccessibleClass> classSelector = new ComboBox<>("Contexto de clase");
    private final Grid<com.wornux.data.entities.training_activity.TrainingActivityAssignment> assignmentsGrid =
            new Grid<>(com.wornux.data.entities.training_activity.TrainingActivityAssignment.class, false);

    public StudentWorkspaceView(
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService,
            StudentWorkspaceService studentWorkspaceService) {
        this.authenticatedAccountService = authenticatedAccountService;
        this.workspaceRoutingService = workspaceRoutingService;
        this.studentWorkspaceService = studentWorkspaceService;

        addClassName("workspace-view");
        classSelector.setItemLabelGenerator(value -> "%s - %s".formatted(value.classCode(), value.className()));
        classSelector.addClassName("workspace-context-select");
        classSelector.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                switchClass(event.getValue());
            }
        });
        assignmentsGrid.addColumn(assignment -> assignment.getTrainingActivity().getTitle())
                .setHeader("Actividad asignada");
        assignmentsGrid.addColumn(assignment -> assignmentStatusLabel(assignment.getStatus())).setHeader("Estado");
        assignmentsGrid.addClassName("workspace-grid");
        assignmentsGrid.setWidthFull();

        var openConversationButton = new Button("Abrir conversación", _ -> UI.getCurrent().navigate(ConversationView.class));
        openConversationButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(
            createHeader(
                "Espacio del estudiante",
                "Mantén la clase activa en contexto, revisa las actividades asignadas y vuelve al tutor cuando necesites razonar con guía."),
            createToolbar(classSelector, openConversationButton),
            createSection("Actividades asignadas", "Trabajo conectado actualmente con el contexto de tu clase activa.", assignmentsGrid));
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

    private Div createHeader(String title, String description) {
        var heading = new H1(title);
        var copy = new Paragraph(description);
        var header = new Div(heading, copy);
        header.addClassName("workspace-hero");
        return header;
    }

    private Div createToolbar(ComboBox<AccessibleClass> selector, Button action) {
        var toolbar = new Div(selector, action);
        toolbar.addClassName("workspace-toolbar");
        return toolbar;
    }

    private Div createSection(String title, String description, Grid<?> grid) {
        var heading = new H2(title);
        var copy = new Paragraph(description);
        var sectionHeader = new Div(heading, copy);
        sectionHeader.addClassName("workspace-section-header");
        var section = new Div(sectionHeader, grid);
        section.addClassName("workspace-section");
        return section;
    }

    private String assignmentStatusLabel(
            com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus status) {
        return switch (status) {
            case ASSIGNED -> "Asignada";
            case STARTED -> "Iniciada";
            case SUBMITTED -> "Entregada";
            case SKIPPED -> "Omitida";
            case EXPIRED -> "Vencida";
            case EXCUSED -> "Justificada";
        };
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
        studentWorkspaceService
                .switchClass(authenticatedAccountService.requireCurrentAccount(), accessibleClass.groupClassMemberId());
        refresh();
    }
}
