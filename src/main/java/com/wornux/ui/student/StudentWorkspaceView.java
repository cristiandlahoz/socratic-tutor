package com.wornux.ui.student;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
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
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.training_activity.TrainingActivityLaunchBus;
import com.wornux.services.workspace.AccessibleClass;
import com.wornux.services.workspace.StudentWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.auth.NoAccessView;
import com.wornux.ui.css.UiCss;
import jakarta.annotation.security.PermitAll;

@Route(value = "student", layout = MainLayout.class)
@PageTitle("Espacio del estudiante")
@PermitAll
@RequiresPermission(AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW)
public class StudentWorkspaceView extends VerticalLayout implements BeforeEnterObserver, AfterNavigationObserver {

    private final AuthenticatedAccountService authenticatedAccountService;
    private final WorkspaceRoutingService workspaceRoutingService;
    private final StudentWorkspaceService studentWorkspaceService;
    private final TrainingActivityLaunchBus trainingActivityLaunchBus;
    private final Div content = new Div();
    private final ComboBox<AccessibleClass> classSelector = new ComboBox<>("Contexto de clase");
    private final Grid<TrainingActivityAssignment> assignmentsGrid = new Grid<>(TrainingActivityAssignment.class, false);
    private AutoCloseable assignmentLaunchSubscription;

    public StudentWorkspaceView(
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService,
            StudentWorkspaceService studentWorkspaceService,
            TrainingActivityLaunchBus trainingActivityLaunchBus) {
        this.authenticatedAccountService = authenticatedAccountService;
        this.workspaceRoutingService = workspaceRoutingService;
        this.studentWorkspaceService = studentWorkspaceService;
        this.trainingActivityLaunchBus = trainingActivityLaunchBus;

        UiCss.WORKSPACE_VIEW.addTo(this);
        configureToolbarFields();
        configureGrid();

        content.add(
            createHeader(
                "Espacio del estudiante",
                "Mantén la clase activa en contexto, revisa las actividades asignadas y vuelve al tutor cuando necesites razonar con guía."),
            createToolbar(),
            assignmentsGrid);
        content.setWidthFull();
        add(content);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        subscribeToAssignmentLaunches(attachEvent.getUI());
        refreshDashboard();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        refreshDashboard();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        unsubscribeFromAssignmentLaunches();
        super.onDetach(detachEvent);
    }

    private void subscribeToAssignmentLaunches(UI ui) {
        unsubscribeFromAssignmentLaunches();
        assignmentLaunchSubscription = trainingActivityLaunchBus.subscribe(event -> ui.access(() -> {
            var selectedClass = classSelector.getValue();
            if (selectedClass == null || !event.affectsGroupClassMember(selectedClass.groupClassMemberId())) {
                return;
            }
            refreshAssignments();
        }));
    }

    private void unsubscribeFromAssignmentLaunches() {
        if (assignmentLaunchSubscription == null) {
            return;
        }
        try {
            assignmentLaunchSubscription.close();
        } catch (Exception ignored) {
            // AutoCloseable is used only as a subscription handle.
        }
        assignmentLaunchSubscription = null;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.STUDENT)) {
            event.forwardTo(NoAccessView.class);
            return;
        }
        refreshDashboard();
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

    private void configureGrid() {
        UiCss.WORKSPACE_GRID.addTo(assignmentsGrid);
        UiCss.WORKSPACE_TENANT_GRID.addTo(assignmentsGrid);
        assignmentsGrid.setWidthFull();
        assignmentsGrid.setSelectionMode(Grid.SelectionMode.NONE);
        assignmentsGrid.setEmptyStateText("No hay actividades asignadas para la clase activa.");
        assignmentsGrid.addColumn(LitRenderer.<TrainingActivityAssignment>of("""
                    <div class="workspace-primary-cell">
                        <span class="workspace-primary-cell-title">${item.title}</span>
                        <span class="workspace-primary-cell-meta">${item.instructions}</span>
                    </div>
                """)
                .withProperty("title", assignment -> assignment.getTrainingActivity().getTitle())
                .withProperty("instructions", assignment -> assignment.getTrainingActivity().getInstructions()))
                .setHeader("Actividad")
                .setComparator(assignment -> assignment.getTrainingActivity().getTitle())
                .setAutoWidth(true)
                .setFlexGrow(1);
        assignmentsGrid.addColumn(LitRenderer.<TrainingActivityAssignment>of("""
                    <span class="workspace-status-badge ${item.statusTone}">${item.statusLabel}</span>
                """)
                .withProperty("statusLabel", assignment -> assignmentStatusLabel(assignment.getStatus()))
                .withProperty("statusTone", assignment -> assignmentStatusTone(assignment.getStatus())))
                .setHeader("Estado")
                .setAutoWidth(true)
                .setFlexGrow(0);
        assignmentsGrid.addColumn(LitRenderer.<TrainingActivityAssignment>of("""
                    <vaadin-button class="workspace-row-action" theme="tertiary small" @click="${openEvaluation}" aria-label="Abrir evaluación para ${item.title}">
                        <vaadin-icon icon="vaadin:comments" slot="prefix"></vaadin-icon>
                        Abrir evaluación
                    </vaadin-button>
                """)
                .withProperty("title", assignment -> assignment.getTrainingActivity().getTitle())
                .withFunction("openEvaluation", this::openEvaluation))
                .setHeader("Opciones")
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private Button primaryButton(String label, Runnable action) {
        var button = new Button(label, _ -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return button;
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

    private void openEvaluation(TrainingActivityAssignment assignment) {
        UI.getCurrent().navigate("training-activity/assignments/%s".formatted(assignment.getId()));
    }

    private void refreshDashboard() {
        var account = authenticatedAccountService.requireCurrentAccount();
        var classes = studentWorkspaceService.listStudentClasses(account);
        classSelector.setItems(classes);
        if (!classes.isEmpty() && classSelector.getValue() == null) {
            classSelector.setValue(classes.getFirst());
        }
        refreshAssignments(account);
    }

    private void refreshAssignments() {
        refreshAssignments(authenticatedAccountService.requireCurrentAccount());
    }

    private void refreshAssignments(Account account) {
        assignmentsGrid.setItems(studentWorkspaceService.listAssignments(account));
    }

    private void switchClass(AccessibleClass accessibleClass) {
        if (accessibleClass == null) {
            return;
        }
        studentWorkspaceService
                .switchClass(authenticatedAccountService.requireCurrentAccount(), accessibleClass.groupClassMemberId());
        refreshDashboard();
    }
}
