package com.wornux.ui.training_activity;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.auth.NoAccessView;
import com.wornux.ui.css.UiCss;

@Route(value = "training-activities", layout = MainLayout.class)
@RequiresPermission(AppPermission.TRAINING_ACTIVITY_CREATE)
public class TrainingActivityView extends Composite<Div> implements BeforeEnterObserver, AfterNavigationObserver {

    private static final Locale SPANISH_LOCALE = Locale.of("es", "DO");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", SPANISH_LOCALE);
    public static final String OPEN_ACTIVITY_QUERY_PARAMETER = "trainingActivity";

    private final transient AuthenticatedAccountService authenticatedAccountService;
    private final transient TrainingActivityService trainingActivityService;
    private final transient WorkspaceRoutingService workspaceRoutingService;

    private final TextField titleField = new TextField("Título");
    private final TextArea instructionField = new TextArea("Instrucciones");
    private final Button saveButton = new Button("Guardar borrador");
    private final Button deleteButton = new Button("Eliminar");
    private final Button launchButton = new Button("Lanzar actividad");
    private final Grid<TrainingActivity> grid = new Grid<>(TrainingActivity.class, false);

    private UUID pendingDialogActivityId;
    private TrainingActivityDialog activeDialog;
    public TrainingActivityView(
            TrainingActivityService trainingActivityService,
            WorkspaceRoutingService workspaceRoutingService,
            AuthenticatedAccountService authenticatedAccountService) {
        this.trainingActivityService = trainingActivityService;
        this.workspaceRoutingService = workspaceRoutingService;
        this.authenticatedAccountService = authenticatedAccountService;

        var content = getContent();
        UiCss.TRAINING_ACTIVITY_VIEW.addTo(content);

        var layout = new VerticalLayout(buildHeader(), buildFormCard(), buildGridCard());
        layout.addClassName("training-activity-content");
        layout.setWidthFull();
        layout.setPadding(false);
        layout.setSpacing(true);

        var pageContent = new Div(layout);
        pageContent.setWidthFull();

        content.add(pageContent);
        refreshGrid();
    }

    private Div buildHeader() {
        var title = new H2("Actividades formativas");
        var description = new Span(
                "Crea actividades formativas, lanza borradores para la clase activa y notifica a los estudiantes por correo.");
        UiCss.TRAINING_ACTIVITY_DESCRIPTION.addTo(description);
        var header = new Div(title, description);
        UiCss.TRAINING_ACTIVITY_HEADER.addTo(header);
        return header;
    }

    private Div buildFormCard() {
        titleField.setWidthFull();
        titleField.setValueChangeMode(ValueChangeMode.EAGER);
        instructionField.setWidthFull();
        instructionField.setMinHeight("5rem");
        instructionField.setMaxHeight("5rem");
        instructionField.setValueChangeMode(ValueChangeMode.EAGER);
        saveButton.addThemeVariants(ButtonVariant.PRIMARY);
        saveButton.setIcon(new Icon(VaadinIcon.PLUS));
        saveButton.addClickShortcut(Key.ENTER).listenOn(instructionField);
        saveButton.addClickListener(_ -> onSave());
        var card = new Div(titleField, instructionField, saveButton);
        UiCss.TRAINING_ACTIVITY_FORM_CARD.addTo(card);
        return card;
    }

    private Div buildGridCard() {
        grid.addColumn(TrainingActivity::getTitle)
                .setHeader("Título")
                .setWidth("0")
                .setFlexGrow(1)
                .setSortable(true)
                .setAutoWidth(false);

        grid.addColumn(TrainingActivity::getInstructions)
                .setHeader("Instrucciones")
                .setWidth("0")
                .setFlexGrow(2)
                .setAutoWidth(false);

        grid.addColumn(new ComponentRenderer<>(this::renderStatusBadge))
                .setHeader("Estado")
                .setWidth("8rem")
                .setFlexGrow(0)
                .setAutoWidth(false);

        grid.addColumn(act -> act.getCreatedAt()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .format(DATE_FORMATTER))
                .setHeader("Creado")
                .setWidth("11rem")
                .setFlexGrow(0)
                .setAutoWidth(false);

        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        grid.addClassName("training-activity-main-grid");
        grid.setWidthFull();
        grid.setHeightFull();

        grid.addItemDoubleClickListener(event -> openActivityDialog(event.getItem(), false));

        grid.asSingleSelect().addValueChangeListener(event -> {
            var selected = event.getValue();
            deleteButton.setEnabled(selected != null);
            launchButton.setEnabled(selected != null && selected.getStatus() == TrainingActivityLifecycleStatus.DRAFT);
        });

        deleteButton.setEnabled(false);
        deleteButton.addThemeVariants(ButtonVariant.ERROR);
        deleteButton.addClickListener(_ -> onDeleteSelected());

        launchButton.setEnabled(false);
        launchButton.addThemeVariants(ButtonVariant.PRIMARY);
        launchButton.setIcon(new Icon(VaadinIcon.PLAY));
        launchButton.addClickListener(_ -> onLaunchSelected());

        var toolbar = new HorizontalLayout(deleteButton, launchButton);
        toolbar.addClassName("training-activity-actions-row");
        toolbar.setPadding(false);
        toolbar.setSpacing(true);

        var card = new Div(grid, toolbar);
        UiCss.TRAINING_ACTIVITY_GRID_CARD.addTo(card);
        return card;
    }

    private Span renderStatusBadge(TrainingActivity activity) {
        var badge = new Span(switch (activity.getStatus()) {
            case DRAFT -> "Borrador";
            case PUBLISHED -> "Publicada";
            case CLOSED -> "Cerrada";
            case ARCHIVED -> "Archivada";
        });

        badge.getElement().getThemeList().add(switch (activity.getStatus()) {
            case DRAFT -> "badge";
            case PUBLISHED -> "badge primary";
            case CLOSED -> "badge contrast";
            case ARCHIVED -> "badge success";
        });

        return badge;
    }

    private void onSave() {
        var title = titleField.getValue().trim();
        var instruction = instructionField.getValue().trim();
        if (title.isBlank() || instruction.isBlank()) {
            Notification.show("Completa el título y las instrucciones antes de guardar");
            return;
        }
        try {
            trainingActivityService.createPending(title, instruction);
            Notification.show("Actividad formativa guardada");
            titleField.clear();
            instructionField.clear();
            refreshGrid();
        }
        catch (SetupRequiredException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void onDeleteSelected() {
        var activity = grid.asSingleSelect().getValue();
        if (activity == null) {
            return;
        }
        trainingActivityService.delete(activity.getId());
        Notification.show("Actividad formativa eliminada");
        refreshGrid();
    }

    private void onLaunchSelected() {
        var activity = grid.asSingleSelect().getValue();
        if (activity == null) {
            return;
        }
        try {
            var launchedStudents = trainingActivityService.launch(activity.getId());
            Notification.show("Actividad formativa lanzada para %d estudiantes".formatted(launchedStudents));
            refreshGrid();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    public void onActivityUpdated(TrainingActivity activity) {
        refreshGrid();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR)) {
            event.forwardTo(NoAccessView.class);
            return;
        }
        pendingDialogActivityId = event.getLocation()
                .getQueryParameters()
                .getSingleParameter(OPEN_ACTIVITY_QUERY_PARAMETER)
                .map(this::parseUuid)
                .orElse(null);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        if (pendingDialogActivityId == null) {
            return;
        }
        var activityId = pendingDialogActivityId;
        pendingDialogActivityId = null;
        openActivityDialogFromRoute(activityId);
    }

    private void refreshGrid() {
        grid.setItems(trainingActivityService.listAll());
        grid.deselectAll();
    }

    private UUID parseUuid(String rawValue) {
        try {
            return UUID.fromString(rawValue);
        }
        catch (IllegalArgumentException _) {
            return null;
        }
    }

    private void openActivityDialogFromRoute(UUID activityId) {
        if (activityId == null || activeDialog != null) {
            clearDialogAddressBarState();
            return;
        }

        try {
            openActivityDialog(trainingActivityService.get(activityId), true);
        }
        catch (IllegalArgumentException _) {
            clearDialogAddressBarState();
        }
    }

    private void openActivityDialog(TrainingActivity activity, boolean clearAddressBarOnClose) {
        closeActivityDialog(false);
        activeDialog = new TrainingActivityDialog(activity,
                trainingActivityService,
                this::onActivityUpdated,
                () -> closeActivityDialog(clearAddressBarOnClose));
        getContent().add(activeDialog);
    }

    private void closeActivityDialog(boolean clearAddressBarOnClose) {
        if (activeDialog != null) {
            getContent().remove(activeDialog);
            activeDialog = null;
        }
        refreshGrid();
        if (clearAddressBarOnClose) {
            clearDialogAddressBarState();
        }
    }

    private void clearDialogAddressBarState() {
        getUI().ifPresent(
            ui -> ui.getPage().getHistory().replaceState(null, new Location("training-activities", QueryParameters.empty())));
    }
}
