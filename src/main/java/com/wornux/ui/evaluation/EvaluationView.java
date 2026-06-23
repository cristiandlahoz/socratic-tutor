package com.wornux.ui.evaluation;

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
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.wornux.data.entities.evaluation.Evaluation;
import com.wornux.data.entities.evaluation.EvaluationLifecycleStatus;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.services.evaluation.EvaluationService;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;

@Route(value = "evaluations", layout = MainLayout.class)
public class EvaluationView extends Composite<Div> implements BeforeEnterObserver, AfterNavigationObserver {

    private static final Locale SPANISH_LOCALE = Locale.of("es", "DO");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", SPANISH_LOCALE);
    public static final String OPEN_EVALUATION_QUERY_PARAMETER = "evaluation";

    private final transient AuthenticatedAccountService authenticatedAccountService;
    private final transient EvaluationService evaluationService;
    private final transient WorkspaceRoutingService workspaceRoutingService;
    private final TextField titleField = new TextField("Title");
    private final TextArea instructionField = new TextArea("Instructions");
    private final Button saveButton = new Button("Save draft");
    private final Grid<Evaluation> grid = new Grid<>(Evaluation.class, false);
    private final Button deleteButton = new Button("Delete");
    private final Button launchButton = new Button("Execution blocked by UC-002 follow-up");
    private UUID pendingDialogEvaluationId;
    private EvaluationDialog openDialog;

    public EvaluationView(
            EvaluationService evaluationService,
            WorkspaceRoutingService workspaceRoutingService,
            AuthenticatedAccountService authenticatedAccountService) {
        this.evaluationService = evaluationService;
        this.workspaceRoutingService = workspaceRoutingService;
        this.authenticatedAccountService = authenticatedAccountService;

        var content = getContent();
        content.addClassName("evaluation-view");
        var layout = new VerticalLayout(buildHeader(), buildFormCard(), buildGridCard());
        layout.setPadding(false);
        layout.setSpacing(true);
        content.add(layout);
        refreshGrid();
    }

    private Div buildHeader() {
        var title = new H2("Formative Activities");
        title.addClassNames(LumoUtility.Margin.NONE);
        var description = new Span(
                "Manage class-scoped formative activity definitions on the target ERD. Assignment execution remains blocked until the target model gains dedicated payload persistence.");
        description.addClassName("evaluation-description");
        var header = new Div(title, description);
        header.addClassName("evaluation-header");
        return header;
    }

    private Div buildFormCard() {
        titleField.setWidthFull();
        titleField.setValueChangeMode(ValueChangeMode.EAGER);
        instructionField.setWidthFull();
        instructionField.setMinHeight("8rem");
        instructionField.setValueChangeMode(ValueChangeMode.EAGER);
        saveButton.addThemeVariants(ButtonVariant.PRIMARY);
        saveButton.setIcon(new Icon(VaadinIcon.PLUS));
        saveButton.addClickShortcut(Key.ENTER).listenOn(instructionField);
        saveButton.addClickListener(_ -> onSave());
        var card = new Div(titleField, instructionField, saveButton);
        card.addClassName("evaluation-form-card");
        return card;
    }

    private Div buildGridCard() {
        grid.addColumn(Evaluation::getTitle).setHeader("Title").setAutoWidth(true).setSortable(true);
        grid.addColumn(Evaluation::getInstructions).setHeader("Instructions").setWidth("24rem").setFlexGrow(1);
        grid.addColumn(new ComponentRenderer<>(this::renderStatusBadge)).setHeader("Status").setWidth("8rem");
        grid.addColumn(eval -> eval.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime().format(DATE_FORMATTER))
                .setHeader("Created")
                .setWidth("12rem");
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        grid.setWidthFull();
        grid.addItemDoubleClickListener(event -> openEvaluationDialog(event.getItem(), false));
        grid.asSingleSelect().addValueChangeListener(event -> deleteButton.setEnabled(event.getValue() != null));

        deleteButton.setEnabled(false);
        deleteButton.addThemeVariants(ButtonVariant.ERROR);
        deleteButton.addClickListener(_ -> onDeleteSelected());

        launchButton.setEnabled(false);
        var toolbar = new HorizontalLayout(deleteButton, launchButton);
        toolbar.setPadding(false);

        var card = new Div(grid, toolbar);
        card.addClassName("evaluation-grid-card");
        return card;
    }

    private Span renderStatusBadge(Evaluation evaluation) {
        var badge = new Span(switch (evaluation.getStatus()) {
            case DRAFT -> "Draft";
            case PUBLISHED -> "Published";
            case CLOSED -> "Closed";
            case ARCHIVED -> "Archived";
        });
        badge.getElement().getThemeList().add(switch (evaluation.getStatus()) {
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
            Notification.show("Complete the title and instructions before saving");
            return;
        }
        try {
            evaluationService.createPending(title, instruction);
            Notification.show("Formative activity saved");
            titleField.clear();
            instructionField.clear();
            refreshGrid();
        }
        catch (SetupRequiredException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void onDeleteSelected() {
        var evaluation = grid.asSingleSelect().getValue();
        if (evaluation == null) {
            return;
        }
        evaluationService.delete(evaluation.getId());
        Notification.show("Formative activity deleted");
        refreshGrid();
    }

    public void onEvaluationUpdated(Evaluation evaluation) {
        refreshGrid();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR)) {
            event.forwardTo("no-access");
            return;
        }
        pendingDialogEvaluationId = event.getLocation().getQueryParameters().getSingleParameter(OPEN_EVALUATION_QUERY_PARAMETER)
                .map(this::parseUuid)
                .orElse(null);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        if (pendingDialogEvaluationId == null) {
            return;
        }
        var evaluationId = pendingDialogEvaluationId;
        pendingDialogEvaluationId = null;
        openEvaluationDialogFromRoute(evaluationId);
    }

    private void refreshGrid() {
        grid.setItems(evaluationService.listAll());
    }

    private UUID parseUuid(String rawValue) {
        try {
            return UUID.fromString(rawValue);
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void openEvaluationDialogFromRoute(UUID evaluationId) {
        if (evaluationId == null || openDialog != null) {
            clearDialogAddressBarState();
            return;
        }
        try {
            openEvaluationDialog(evaluationService.get(evaluationId), true);
        }
        catch (IllegalArgumentException ignored) {
            clearDialogAddressBarState();
        }
    }

    private void openEvaluationDialog(Evaluation evaluation, boolean clearAddressBarOnClose) {
        openDialog = new EvaluationDialog(evaluation, evaluationService, this::onEvaluationUpdated, () -> closeEvaluationDialog(clearAddressBarOnClose));
        getContent().add(openDialog);
    }

    private void closeEvaluationDialog(boolean clearAddressBarOnClose) {
        if (openDialog != null) {
            getContent().remove(openDialog);
            openDialog = null;
        }
        if (clearAddressBarOnClose) {
            clearDialogAddressBarState();
        }
    }

    private void clearDialogAddressBarState() {
        getUI().ifPresent(ui -> ui.getPage().getHistory().replaceState(null, new Location("evaluations", QueryParameters.empty())));
    }
}
