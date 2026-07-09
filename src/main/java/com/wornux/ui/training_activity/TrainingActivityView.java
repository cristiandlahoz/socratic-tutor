package com.wornux.ui.training_activity;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.services.training_activity.TrainingActivitySaveCommand;
import com.wornux.services.training_activity.instruction_review.InstructionQualityReviewException;
import com.wornux.services.training_activity.instruction_review.InstructionReviewSnapshotDto;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.auth.NoAccessView;
import com.wornux.ui.css.UiCss;
import com.wornux.ui.training_activity.instruction_review.InstructionLinterEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Route(value = "training-activities", layout = MainLayout.class)
@RequiresPermission(value = AppPermission.TRAINING_ACTIVITY_CREATE, workspace = WorkspaceDestination.PROFESSOR)
public class TrainingActivityView extends Composite<Div> implements BeforeEnterObserver, AfterNavigationObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrainingActivityView.class);

    private static final Locale SPANISH_LOCALE = Locale.of("es", "DO");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", SPANISH_LOCALE);
    public static final String OPEN_ACTIVITY_QUERY_PARAMETER = "trainingActivity";

    private final transient AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final transient TrainingActivityService trainingActivityService;
    private final transient SafeBrowserModeService safeBrowserModeService;
    private final transient SafeBrowserAssignmentStateBus assignmentStateBus;
    private final transient WorkspaceRoutingService workspaceRoutingService;

    private final TextField titleField = new TextField("Título");
    private final InstructionLinterEditor instructionField = new InstructionLinterEditor();
    private final Checkbox safeBrowserField = new Checkbox("Safe Browser Mode");
    private final Button saveButton = new Button("Guardar borrador");
    private final Button deleteButton = new Button("Eliminar");
    private final Button launchButton = new Button("Lanzar actividad");
    private final Grid<TrainingActivity> grid = new Grid<>(TrainingActivity.class, false);

    private UUID pendingDialogActivityId;
    private TrainingActivityDialog activeDialog;
    private InstructionReviewSnapshotDto lastReviewSnapshot;
    private String lastReviewedTitle = "";
    private String lastReviewedInstructions = "";
    private boolean reviewInProgress;
    private boolean saveInProgress;

    public TrainingActivityView(
            TrainingActivityService trainingActivityService,
            SafeBrowserModeService safeBrowserModeService,
            SafeBrowserAssignmentStateBus assignmentStateBus,
            WorkspaceRoutingService workspaceRoutingService,
            AuthenticatedUserContextUtils authenticatedUserContextUtils) {
        this.trainingActivityService = trainingActivityService;
        this.safeBrowserModeService = safeBrowserModeService;
        this.assignmentStateBus = assignmentStateBus;
        this.workspaceRoutingService = workspaceRoutingService;
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;

        var content = getContent();
        UiCss.TRAINING_ACTIVITY_VIEW.addTo(content);
        content.setSizeFull();

        var layout = new VerticalLayout(buildHeader(), buildFormCard(), buildGridCard());
        layout.addClassName("training-activity-content");
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(true);

        var pageContent = new Div(layout);
        pageContent.setSizeFull();

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
        instructionField.setMinHeight("9rem");
        titleField.addValueChangeListener(_ -> invalidateLocalReview());
        instructionField.addValueChangeListener(_ -> invalidateLocalReview());
        saveButton.addThemeVariants(ButtonVariant.PRIMARY);
        saveButton.setIcon(new Icon(VaadinIcon.PLUS));
        saveButton.addClickShortcut(Key.ENTER).listenOn(instructionField);
        saveButton.addClickListener(_ -> onSave());
        safeBrowserField.setHelperText("Requiere pantalla completa y monitorea cambios de pestaña durante la evaluación.");
        var card = new Div(titleField, instructionField, safeBrowserField, saveButton);
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

        grid.addColumn(activity -> activity.isSafeBrowserEnabled() ? "Activo" : "No")
                .setHeader("Safe Browser")
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
        if (reviewInProgress || saveInProgress) {
            return;
        }

        var title = titleField.getValue().trim();
        var instruction = instructionField.getValue().trim();
        LOGGER.info(
                "Draft save clicked: titleLength={} instructionLength={} safeBrowserEnabled={}",
                title.length(),
                instruction.length(),
                safeBrowserField.getValue());
        if (title.isBlank() || instruction.isBlank()) {
            LOGGER.info("Draft save blocked locally because title or instruction is blank");
            Notification.show("Completa el título y las instrucciones antes de guardar");
            return;
        }
        if (!reviewMatchesCurrentInput(title, instruction)) {
            reviewDraft(title, instruction);
            return;
        }

        if (isBlockedReview(lastReviewSnapshot)) {
            Notification.show(blockingReviewMessage(lastReviewSnapshot));
            return;
        }

        var confirmedReviewHash = requiresVisibleReviewConfirmation(lastReviewSnapshot)
                ? lastReviewSnapshot.reviewHash()
                : "";
        persistDraft(title, instruction, confirmedReviewHash);
    }

    private void reviewDraft(String title, String instruction) {
        var command = new TrainingActivitySaveCommand(
                title,
                instruction,
                safeBrowserField.getValue());

        reviewInProgress = true;
        saveButton.setEnabled(false);
        saveButton.setText("Revisando instrucciones...");
        instructionField.setReviewing(true);

        try {
            handleReviewCompleted(title, instruction, trainingActivityService.reviewDraft(command), null);
        }
        catch (RuntimeException exception) {
            handleReviewCompleted(title, instruction, null, exception);
        }
    }

    private void handleReviewCompleted(
            String title,
            String instruction,
            InstructionReviewSnapshotDto snapshot,
            Throwable throwable) {
        reviewInProgress = false;
        saveButton.setEnabled(true);
        instructionField.setReviewing(false);

        if (throwable != null) {
            handleSaveException(unwrapCompletionException(throwable));
            updateSaveButtonText();
            return;
        }

        lastReviewSnapshot = snapshot;
        lastReviewedTitle = title;
        lastReviewedInstructions = instruction;
        instructionField.setReviewSnapshot(snapshot);

        if (shouldPersistImmediately(snapshot)) {
            persistDraft(title, instruction, "");
            return;
        }

        if (isBlockedReview(snapshot)) {
            Notification.show(blockingReviewMessage(snapshot));
        }
        else if (snapshot.message() != null && !snapshot.message().isBlank()) {
            Notification.show(snapshot.message());
        }
        updateSaveButtonText();
    }

    private void persistDraft(String title, String instruction, String confirmedReviewHash) {
        if (saveInProgress) {
            return;
        }

        saveInProgress = true;
        saveButton.setEnabled(false);
        saveButton.setText("Guardando borrador...");
        try {
            LOGGER.info("Draft save calling TrainingActivityService.createPending");
            var saved = trainingActivityService.createPending(new TrainingActivitySaveCommand(
                    title,
                    instruction,
                    safeBrowserField.getValue(),
                    confirmedReviewHash));
            LOGGER.info("Draft save persisted activityId={}", saved.getId());
            var snapshot = trainingActivityService.getInstructionReviewSnapshot(saved.getId());
            LOGGER.info(
                    "Draft save retrieved review snapshot: activityId={} reviewStatus={} qualityStatus={} canSave={}",
                    saved.getId(),
                    snapshot.reviewStatus(),
                    snapshot.qualityStatus(),
                    snapshot.canSave());
            refreshGrid();
            Notification.show(saveMessage(snapshot));
            titleField.clear();
            instructionField.clear();
            instructionField.resetReviewState();
            safeBrowserField.clear();
            clearLocalReview();
            updateSaveButtonText();
        }
        catch (RuntimeException exception) {
            handleSaveException(exception);
        }
        finally {
            LOGGER.info("Draft save flow finished");
            saveInProgress = false;
            saveButton.setEnabled(true);
            updateSaveButtonText();
        }
    }

    private void handleSaveException(Throwable throwable) {
        if (throwable instanceof InstructionQualityReviewException exception) {
            LOGGER.warn(
                    "Draft save rejected by instruction review: message={} reviewStatus={} qualityStatus={}",
                    exception.getMessage(),
                    exception.getReviewSnapshot() == null ? null : exception.getReviewSnapshot().reviewStatus(),
                    exception.getReviewSnapshot() == null ? null : exception.getReviewSnapshot().qualityStatus());
            if (exception.getReviewSnapshot() != null) {
                lastReviewSnapshot = exception.getReviewSnapshot();
                lastReviewedTitle = titleField.getValue().trim();
                lastReviewedInstructions = instructionField.getValue().trim();
                instructionField.setReviewSnapshot(exception.getReviewSnapshot());
            }
            Notification.show(exception.getMessage());
            return;
        }
        if (throwable instanceof SetupRequiredException exception) {
            LOGGER.warn("Draft save blocked by missing setup: {}", exception.getMessage());
            Notification.show(exception.getMessage());
            return;
        }
        LOGGER.warn("Draft save failed unexpectedly", throwable);
        Notification.show("No pudimos guardar el borrador. Inténtalo de nuevo.");
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException exception && exception.getCause() != null) {
            return exception.getCause();
        }
        return throwable;
    }

    private boolean reviewMatchesCurrentInput(String title, String instructions) {
        return lastReviewSnapshot != null
                && title.equals(lastReviewedTitle)
                && instructions.equals(lastReviewedInstructions);
    }

    private boolean shouldPersistImmediately(InstructionReviewSnapshotDto snapshot) {
        return isSaveableGoodReview(snapshot);
    }

    private boolean isSaveableGoodReview(InstructionReviewSnapshotDto snapshot) {
        return snapshot != null && snapshot.isSaveableGoodReview();
    }

    private boolean isBlockedReview(InstructionReviewSnapshotDto snapshot) {
        return snapshot != null
                && !shouldPersistImmediately(snapshot)
                && !requiresVisibleReviewConfirmation(snapshot)
                && !snapshot.canSave();
    }

    private boolean requiresVisibleReviewConfirmation(InstructionReviewSnapshotDto snapshot) {
        return snapshot != null && snapshot.requiresVisibleReviewConfirmation();
    }

    private String blockingReviewMessage(InstructionReviewSnapshotDto snapshot) {
        if (snapshot != null && snapshot.message() != null && !snapshot.message().isBlank()) {
            return snapshot.message();
        }
        return "Estas instrucciones no se pueden guardar todavía.";
    }

    private void invalidateLocalReview() {
        if (lastReviewSnapshot == null || reviewInProgress) {
            updateSaveButtonText();
            return;
        }
        if (reviewMatchesCurrentInput(titleField.getValue().trim(), instructionField.getValue().trim())) {
            updateSaveButtonText();
            return;
        }
        clearLocalReview();
        instructionField.markReviewStale();
        updateSaveButtonText();
    }

    private void clearLocalReview() {
        lastReviewSnapshot = null;
        lastReviewedTitle = "";
        lastReviewedInstructions = "";
    }

    private void updateSaveButtonText() {
        if (reviewInProgress) {
            saveButton.setText("Revisando instrucciones...");
            return;
        }
        if (saveInProgress) {
            saveButton.setText("Guardando borrador...");
            return;
        }
        saveButton.setText("Guardar borrador");
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
        var account = authenticatedUserContextUtils.requireCurrentAccount();
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
        grid.setItems(new ArrayList<>(trainingActivityService.listAll()));
        grid.getDataProvider().refreshAll();
        grid.deselectAll();
        getUI().ifPresent(ui -> ui.beforeClientResponse(grid, _ -> grid.getDataProvider().refreshAll()));
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
                safeBrowserModeService,
                assignmentStateBus,
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

    private String saveMessage(InstructionReviewSnapshotDto snapshot) {
        if (snapshot.reviewStatus() == InstructionReviewStatus.COMPLETED_FROM_CACHE) {
            return "Borrador guardado con una revisión válida reutilizada para estas mismas instrucciones.";
        }
        return "Actividad guardada";
    }
}
