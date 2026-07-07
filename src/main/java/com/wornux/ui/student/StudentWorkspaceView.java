package com.wornux.ui.student;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.workspace.AccessibleClass;
import com.wornux.services.workspace.StudentWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.auth.NoAccessView;
import com.wornux.ui.conversation.ConversationView;
import com.wornux.ui.css.UiCss;
import jakarta.annotation.security.PermitAll;

@Route(value = "student", layout = MainLayout.class)
@PageTitle("Espacio del estudiante")
@PermitAll
@RequiresPermission(AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW)
public class StudentWorkspaceView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final WorkspaceRoutingService workspaceRoutingService;
    private final StudentWorkspaceService studentWorkspaceService;
    private final ComboBox<AccessibleClass> classSelector = new ComboBox<>("Contexto de clase");
    private final Grid<TrainingActivityAssignment> assignmentsGrid =
            new Grid<>(TrainingActivityAssignment.class, false);

    public StudentWorkspaceView(
            AuthenticatedUserContextUtils authenticatedUserContextUtils,
            WorkspaceRoutingService workspaceRoutingService,
            StudentWorkspaceService studentWorkspaceService) {
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
        this.workspaceRoutingService = workspaceRoutingService;
        this.studentWorkspaceService = studentWorkspaceService;

        UiCss.WORKSPACE_VIEW.addTo(this);
        configureToolbarFields();
        configureGrid();

        add(
            createHeader(
                "Espacio del estudiante",
                "Mantén la clase activa en contexto, revisa las actividades asignadas y vuelve al tutor cuando necesites razonar con guía."),
            createToolbar(),
            assignmentsGrid);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedUserContextUtils.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.STUDENT)) {
            event.forwardTo(NoAccessView.class);
            return;
        }
        refresh();
    }

    private Div createHeader(String title, String description) {
        var heading = new H1(title);
        var copy = new Paragraph(description);
        var header = new Div(heading, copy);
        UiCss.WORKSPACE_HERO.addTo(header);
        UiCss.WORKSPACE_HERO_PLAIN.addTo(header);
        return header;
    }

    private void configureToolbarFields() {
        classSelector.setItemLabelGenerator(value -> "%s - %s".formatted(value.classCode(), value.className()));
        UiCss.WORKSPACE_CONTEXT_SELECT.addTo(classSelector);
        classSelector.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                switchClass(event.getValue());
            }
        });
    }

    private Component createToolbar() {
        var toolbar = new HorizontalLayout(classSelector);
        UiCss.WORKSPACE_GRID_TOOLBAR.addTo(toolbar);
        toolbar.setPadding(false);
        toolbar.setMargin(false);
        toolbar.setSpacing(false);
        return toolbar;
    }

    private void openConversation() {
        UI.getCurrent().navigate(ConversationView.class);
    }

    private void configureGrid() {
        UiCss.WORKSPACE_GRID.addTo(assignmentsGrid);
        UiCss.WORKSPACE_TENANT_GRID.addTo(assignmentsGrid);
        assignmentsGrid.setWidthFull();
        assignmentsGrid.setSelectionMode(Grid.SelectionMode.NONE);
        assignmentsGrid.setEmptyStateText("No hay actividades asignadas para la clase activa.");
        assignmentsGrid
                .addColumn(
                    LitRenderer
                            .<TrainingActivityAssignment>of(
                                """
                                    <div class="workspace-primary-cell">
                                        <span class="workspace-primary-cell-title">${item.title}</span>
                                        <span class="workspace-primary-cell-meta">${item.instructions}</span>
                                    </div>
                                """)
                            .withProperty("title", assignment -> assignment.getTrainingActivity().getTitle())
                            .withProperty(
                                "instructions",
                                assignment -> assignment.getTrainingActivity().getInstructions()))
                .setHeader("Actividad")
                .setComparator(assignment -> assignment.getTrainingActivity().getTitle())
                .setAutoWidth(true)
                .setFlexGrow(1);
        assignmentsGrid
                .addColumn(
                    LitRenderer
                            .<TrainingActivityAssignment>of(
                                """
                                    <span class="workspace-status-badge ${item.statusTone}">${item.statusLabel}</span>
                                """)
                            .withProperty("statusLabel", assignment -> assignmentStatusLabel(assignment.getStatus()))
                            .withProperty("statusTone", assignment -> assignmentStatusTone(assignment.getStatus())))
                .setHeader("Estado")
                .setAutoWidth(true)
                .setFlexGrow(0);
        assignmentsGrid
                .addColumn(
                    LitRenderer
                            .<TrainingActivityAssignment>of(
                                """
                                    <vaadin-button class="workspace-row-action" theme="tertiary small" @click="${openTutor}" aria-label="Abrir tutor para ${item.title}">
                                        <vaadin-icon src="/icons/IconConvo.svg" slot="prefix" aria-hidden="true"></vaadin-icon>
                                        Abrir tutor
                                    </vaadin-button>
                                """)
                            .withProperty("title", assignment -> assignment.getTrainingActivity().getTitle())
                            .withFunction("openTutor", _ -> openConversation()))
                .setHeader("Opciones")
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private String assignmentStatusLabel(TrainingActivityAssignmentStatus status) {
        return switch (status) {
            case ASSIGNED -> "Asignada";
            case STARTED -> "Iniciada";
            case SUBMITTED -> "Entregada";
            case SKIPPED -> "Omitida";
            case EXPIRED -> "Vencida";
            case EXCUSED -> "Justificada";
        };
    }

    private String assignmentStatusTone(TrainingActivityAssignmentStatus status) {
        return switch (status) {
            case STARTED, SUBMITTED -> "is-active";
            case ASSIGNED, SKIPPED, EXPIRED, EXCUSED -> "is-open";
        };
    }

    private void refresh() {
        var account = authenticatedUserContextUtils.requireCurrentAccount();
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
                .switchClass(authenticatedUserContextUtils.requireCurrentAccount(), accessibleClass.groupClassMemberId());
        refresh();
    }
}
