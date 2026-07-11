package com.wornux.ui.student;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.training_activity.TrainingActivityLaunchedBus;
import com.wornux.services.workspace.AccessibleClass;
import com.wornux.services.workspace.StudentWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.ui.MainLayout;
import com.wornux.ui.components.WorkspaceViewShell;
import com.wornux.ui.css.UiCss;
import jakarta.annotation.security.PermitAll;

@Route(value = "student", layout = MainLayout.class)
@PageTitle("Espacio del estudiante")
@PermitAll
@RequiresPermission(value = AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW, workspace = WorkspaceDestination.STUDENT)
public class StudentWorkspaceView extends WorkspaceViewShell implements AfterNavigationObserver {

    private final transient AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final transient StudentWorkspaceService studentWorkspaceService;
    private final transient TrainingActivityLaunchedBus activityLaunchedBus;
    private final ComboBox<AccessibleClass> classSelector = new ComboBox<>("Contexto de clase");
    private final Grid<TrainingActivityAssignment> assignmentsGrid =
            new Grid<>(TrainingActivityAssignment.class, false);
    private AutoCloseable activityLaunchedSubscription;
    private Registration assignmentRefreshPollRegistration;

    public StudentWorkspaceView(
            AuthenticatedUserContextUtils authenticatedUserContextUtils,
            StudentWorkspaceService studentWorkspaceService,
            TrainingActivityLaunchedBus activityLaunchedBus) {
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
        this.studentWorkspaceService = studentWorkspaceService;
        this.activityLaunchedBus = activityLaunchedBus;

        configureToolbarFields();
        configureGrid();

        setWorkspaceContent(
            "Espacio del estudiante",
            "Mantén la clase activa en contexto, revisa las actividades asignadas y vuelve al tutor cuando necesites razonar con guía.",
            createToolbar(),
            assignmentsGrid);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        subscribeToAssignmentLaunches(attachEvent.getUI());
        startAssignmentRefreshPolling(attachEvent.getUI());
        refresh();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        refresh();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        unsubscribeFromAssignmentLaunches();
        stopAssignmentRefreshPolling();
        super.onDetach(detachEvent);
    }

    private void subscribeToAssignmentLaunches(UI ui) {
        unsubscribeFromAssignmentLaunches();
        activityLaunchedSubscription = activityLaunchedBus.subscribe(notification -> ui.access(() -> {
            var selectedClass = classSelector.getValue();
            if (selectedClass == null || !notification.affectsGroupClassMember(selectedClass.groupClassMemberId())) {
                return;
            }
            refreshAssignments();
        }));
    }

    private void unsubscribeFromAssignmentLaunches() {
        if (activityLaunchedSubscription == null) {
            return;
        }
        try {
            activityLaunchedSubscription.close();
        }
        catch (Exception _) {
            // Best effort: UI listeners are removed during detach.
        }
        activityLaunchedSubscription = null;
    }

    private void startAssignmentRefreshPolling(UI ui) {
        stopAssignmentRefreshPolling();
        assignmentRefreshPollRegistration = ui.addPollListener(_ -> refreshAssignments());
        ui.setPollInterval(5_000);
    }

    private void stopAssignmentRefreshPolling() {
        if (assignmentRefreshPollRegistration != null) {
            assignmentRefreshPollRegistration.remove();
            assignmentRefreshPollRegistration = null;
        }
        getUI().ifPresent(ui -> ui.setPollInterval(-1));
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
        return toolbar(classSelector);
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
                .setAutoWidth(false)
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

    private String assignmentStatusLabel(TrainingActivityAssignmentStatus status) {
        return switch (status) {
            case ASSIGNED -> "Asignada";
            case STARTING -> "Preparando pregunta";
            case WAITING_FOR_ANSWER -> "Esperando respuesta";
            case WAITING_FOR_TUTOR -> "Analizando respuesta";
            case TEMPORARILY_UNAVAILABLE -> "Tutor no disponible temporalmente";
            case SUBMITTED -> "Entregada";
            case SKIPPED -> "Omitida";
            case EXPIRED -> "Vencida";
            case EXCUSED -> "Justificada";
        };
    }

    private String assignmentStatusTone(TrainingActivityAssignmentStatus status) {
        return switch (status) {
            case STARTING, WAITING_FOR_ANSWER, WAITING_FOR_TUTOR, SUBMITTED -> "is-active";
            case ASSIGNED, TEMPORARILY_UNAVAILABLE, SKIPPED, EXPIRED, EXCUSED -> "is-open";
        };
    }

    private void openEvaluation(TrainingActivityAssignment assignment) {
        UI.getCurrent().navigate("training-activity/assignments/%s".formatted(assignment.getId()));
    }

    private void refresh() {
        var account = authenticatedUserContextUtils.requireCurrentAccount();
        var classes = studentWorkspaceService.listStudentClasses(account);
        classSelector.setItems(classes);
        if (!classes.isEmpty() && classSelector.getValue() == null) {
            classSelector.setValue(classes.getFirst());
        }
        refreshAssignments(account);
    }

    private void refreshAssignments() {
        refreshAssignments(authenticatedUserContextUtils.requireCurrentAccount());
    }

    private void refreshAssignments(Account account) {
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
