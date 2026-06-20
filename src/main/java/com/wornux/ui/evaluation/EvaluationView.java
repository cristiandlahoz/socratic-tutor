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
import com.wornux.data.entities.Evaluation;
import com.wornux.data.enums.EvaluationStatus;
import com.wornux.infrastructure.web.BrowserClientService;
import com.wornux.services.evaluation.EvaluationQuestionGenerationService;
import com.wornux.services.evaluation.EvaluationRunService;
import com.wornux.services.evaluation.EvaluationService;
import com.wornux.ui.MainLayout;
import jakarta.annotation.security.PermitAll;

@Route(value = "evaluations", layout = MainLayout.class)
@PermitAll
public class EvaluationView extends Composite<Div> implements BeforeEnterObserver, AfterNavigationObserver {

    private static final Locale SPANISH_LOCALE = Locale.of("es", "DO");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", SPANISH_LOCALE);
    public static final String OPEN_EVALUATION_QUERY_PARAMETER = "evaluation";

    private final transient EvaluationService evaluationService;
    private final transient EvaluationRunService runService;
    private final transient EvaluationQuestionGenerationService questionGenerationService;
    private final transient BrowserClientService browserClientService;

    private final TextField titleField = new TextField("Título");
    private final TextArea instructionField = new TextArea("Instrucción");
    private final Button saveButton = new Button("Guardar borrador");
    private final Grid<Evaluation> grid = new Grid<>(Evaluation.class, false);
    private final Button generateButton = new Button("Generar Preguntas");
    private final Button deleteButton = new Button("Eliminar");
    private final Button launchButton = new Button("Lanzar actividad formativa");

    private UUID pendingDialogEvaluationId;
    private EvaluationDialog openDialog;

    public EvaluationView(
            EvaluationService evaluationService,
            EvaluationRunService runService,
            EvaluationQuestionGenerationService questionGenerationService,
            BrowserClientService browserClientService) {
        this.evaluationService = evaluationService;
        this.runService = runService;
        this.questionGenerationService = questionGenerationService;
        this.browserClientService = browserClientService;

        var content = getContent();
        content.addClassName("evaluation-view");

        var header = buildHeader();
        var formCard = buildFormCard();
        var actionsRow = buildActionsRow();
        var gridCard = buildGridCard();
        var bottomActions = buildBottomActions();

        var layout = new VerticalLayout(header, formCard, actionsRow, gridCard, bottomActions);
        layout.setPadding(false);
        layout.setSpacing(true);
        content.add(layout);

        refreshGrid();
    }

    private Div buildHeader() {
        var title = new H2("Actividades formativas");
        title.addClassNames(LumoUtility.Margin.NONE);

        var description = new Span(
                "Crea y gestiona actividades formativas diagnósticas. Escribe las instrucciones, genera preguntas y lanza la actividad formativa.");
        description.addClassName("evaluation-description");

        var header = new Div(title, description);
        header.addClassName("evaluation-header");
        return header;
    }

    private Div buildFormCard() {
        titleField.setWidthFull();
        titleField.setValueChangeMode(ValueChangeMode.EAGER);
        titleField.setPlaceholder("Título de la actividad formativa");

        instructionField.setWidthFull();
        instructionField.setMinHeight("8rem");
        instructionField.setValueChangeMode(ValueChangeMode.EAGER);
        instructionField.setPlaceholder("Escribe las instrucciones para la actividad formativa diagnóstica...");

        saveButton.addThemeVariants(ButtonVariant.PRIMARY);
        saveButton.setIcon(new Icon(VaadinIcon.PLUS));
        saveButton.addClickShortcut(Key.ENTER).listenOn(instructionField);
        saveButton.addClickListener(_ -> onSave());
        updateSaveButton();

        titleField.addValueChangeListener(_ -> updateSaveButton());
        instructionField.addValueChangeListener(_ -> updateSaveButton());

        var card = new Div(titleField, instructionField, saveButton);
        card.addClassName("evaluation-form-card");
        return card;
    }

    private Div buildActionsRow() {
        var row = new Div();
        row.addClassName("evaluation-actions-row");
        return row;
    }

    private Div buildGridCard() {
        grid.addColumn(Evaluation::getTitle).setHeader("Título").setAutoWidth(true).setSortable(true);
        grid.addColumn(eval -> {
            var instr = eval.getInstruction();
            return instr.length() > 80 ? instr.substring(0, 80) + "..." : instr;
        }).setHeader("Instrucción").setWidth("20rem").setFlexGrow(1);
        grid.addColumn(new ComponentRenderer<>(this::renderQuestionsCount)).setHeader("Pregs.").setWidth("6rem");
        grid.addColumn(new ComponentRenderer<>(this::renderStatusBadge)).setHeader("Estado").setWidth("8rem");
        grid.addColumn(
            eval -> eval.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime().format(DATE_FORMATTER))
                .setHeader("Creado")
                .setWidth("12rem");
        grid.addColumn(new ComponentRenderer<>(this::renderDeleteButton))
                .setHeader("Acción")
                .setWidth("5rem")
                .setFlexGrow(0);

        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setWidthFull();
        grid.setMinHeight("12rem");

        grid.asSingleSelect().addValueChangeListener(event -> onSelectionChange(event.getValue()));

        grid.addItemDoubleClickListener(event -> {
            if (openDialog != null)
                return;
            openEvaluationDialog(event.getItem(), false);
        });

        generateButton.setIcon(new Icon(VaadinIcon.QUESTION));
        generateButton.addThemeVariants(ButtonVariant.PRIMARY);
        generateButton.setEnabled(false);
        generateButton.addClickListener(_ -> onGenerateQuestions());

        deleteButton.setIcon(new Icon(VaadinIcon.TRASH));
        deleteButton.addThemeVariants(ButtonVariant.ERROR);
        deleteButton.setEnabled(false);
        deleteButton.addClickListener(_ -> onDeleteSelected());

        launchButton.setIcon(new Icon(VaadinIcon.PLAY));
        launchButton.addThemeVariants(ButtonVariant.SUCCESS);
        launchButton.setVisible(false);
        launchButton.addClickListener(_ -> onLaunch());

        var toolbar = new HorizontalLayout(generateButton, deleteButton, launchButton);
        toolbar.setPadding(false);

        var card = new Div(grid, toolbar);
        card.addClassName("evaluation-grid-card");
        return card;
    }

    private Div buildBottomActions() {
        return new Div();
    }

    private Span renderQuestionsCount(Evaluation eval) {
        var json = eval.getQuestionsJson();
        if (json == null || json.isBlank()) {
            return new Span("—");
        }
        try {
            var questions = questionGenerationService.fromJson(json);
            var span = new Span(String.valueOf(questions.size()));
            span.getElement().getStyle().set("font-weight", "bold");
            return span;
        }
        catch (Exception e) {
            return new Span("?");
        }
    }

    private Span renderStatusBadge(Evaluation eval) {
        var badge = new Span(switch (eval.getStatus()) {
            case PENDING -> "Pendiente";
            case RUNNING -> "En curso";
            case COMPLETED -> "Completada";
            case FAILED -> "Fallida";
        });
        badge.getElement().getThemeList().add(switch (eval.getStatus()) {
            case PENDING -> "badge";
            case RUNNING -> "badge primary";
            case COMPLETED -> "badge success";
            case FAILED -> "badge error";
        });
        return badge;
    }

    private Button renderDeleteButton(Evaluation eval) {
        var button = new Button(new Icon(VaadinIcon.TRASH));
        button.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR);
        button.getElement().setAttribute("aria-label", "Eliminar " + eval.getTitle());
        button.addClickListener(_ -> confirmAndDelete(eval));
        return button;
    }

    private void confirmAndDelete(Evaluation eval) {
        var dialog = new com.vaadin.flow.component.dialog.Dialog();
        dialog.setHeaderTitle("Eliminar actividad formativa");

        var message = new com.vaadin.flow.component.html.Span(
                "¿Estás seguro de que querés eliminar \"" + eval.getTitle() + "\"? Esta acción no se puede deshacer.");

        var confirmButton = new Button("Eliminar", _ -> {
            evaluationService.delete(eval.getId());
            Notification.show("Actividad formativa eliminada");
            refreshGrid();
            clearSelection();
            dialog.close();
        });
        confirmButton.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.PRIMARY);

        var cancelButton = new Button("Cancelar", _ -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.TERTIARY);

        dialog.add(message);
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    private void onSave() {
        var title = titleField.getValue().trim();
        var instruction = instructionField.getValue().trim();

        if (title.isBlank() || instruction.isBlank()) {
            Notification.show("Completá el título y la instrucción antes de guardar");
            return;
        }

        evaluationService.createPending(title, instruction);
        Notification.show("Actividad formativa guardada");

        titleField.clear();
        instructionField.clear();
        refreshGrid();
    }

    private void onSelectionChange(Evaluation evaluation) {
        boolean hasSelection = evaluation != null;
        generateButton.setEnabled(hasSelection && evaluation.getQuestionsJson() == null);
        deleteButton.setEnabled(hasSelection);
        boolean canLaunch = hasSelection && evaluation.getQuestionsJson() != null;
        launchButton.setVisible(canLaunch);
        if (canLaunch) {
            launchButton.setText(
                evaluation.getStatus() == EvaluationStatus.COMPLETED
                        ? "Relanzar actividad formativa"
                        : "Lanzar actividad formativa");
        }
    }

    private void onGenerateQuestions() {
        var evaluation = grid.asSingleSelect().getValue();
        if (evaluation == null)
            return;

        generateButton.setEnabled(false);
        generateButton.setText("Generando...");

        try {
            var questions = questionGenerationService.generateQuestions(evaluation.getInstruction());
            var json = questionGenerationService.toJson(questions);
            evaluationService.saveQuestions(evaluation.getId(), json);

            Notification.show("Se generaron %d preguntas".formatted(questions.size()));
            refreshGrid();
            var refreshedEvaluation = evaluationService.get(evaluation.getId());
            grid.asSingleSelect().setValue(refreshedEvaluation);
            onSelectionChange(refreshedEvaluation);
        }
        catch (Exception e) {
            Notification.show("Error al generar preguntas: " + e.getMessage());
        }
        finally {
            generateButton.setText("Generar Preguntas");
            var selected = grid.asSingleSelect().getValue();
            generateButton.setEnabled(selected != null && selected.getQuestionsJson() == null);
        }
    }

    private void onDeleteSelected() {
        var evaluation = grid.asSingleSelect().getValue();
        if (evaluation == null)
            return;
        confirmAndDelete(evaluation);
    }

    private void onLaunch() {
        var evaluation = grid.asSingleSelect().getValue();
        if (evaluation == null || evaluation.getQuestionsJson() == null)
            return;

        try {
            var clientId = browserClientService.resolveClientId();
            var run = runService.createRun(evaluation.getId(), clientId, "[]");
            evaluationService.markRunning(evaluation.getId());

            getUI().ifPresent(ui -> ui.navigate(EvaluationChatView.class, run.getId().toString()));
        }
        catch (Exception e) {
            Notification.show("Error al lanzar la actividad formativa: " + e.getMessage());
        }
    }

    public void onEvaluationUpdated(Evaluation evaluation) {
        refreshGrid();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        pendingDialogEvaluationId = event.getLocation()
                .getQueryParameters()
                .getSingleParameter(OPEN_EVALUATION_QUERY_PARAMETER)
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
        var items = evaluationService.listAll();
        grid.setItems(items);
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
            var evaluation = evaluationService.get(evaluationId);
            grid.asSingleSelect().setValue(evaluation);
            onSelectionChange(evaluation);
            openEvaluationDialog(evaluation, true);
        }
        catch (IllegalArgumentException ignored) {
            clearDialogAddressBarState();
        }
    }

    private void openEvaluationDialog(Evaluation evaluation, boolean clearAddressBarOnClose) {
        openDialog = new EvaluationDialog(evaluation,
                evaluationService,
                questionGenerationService,
                this::onEvaluationUpdated,
                () -> closeEvaluationDialog(clearAddressBarOnClose));
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
        getUI().ifPresent(
            ui -> ui.getPage().getHistory().replaceState(null, new Location("evaluations", QueryParameters.empty())));
    }

    private void clearSelection() {
        grid.asSingleSelect().clear();
        generateButton.setEnabled(false);
        deleteButton.setEnabled(false);
        launchButton.setVisible(false);
        launchButton.setText("Lanzar actividad formativa");
    }

    private void updateSaveButton() {
        boolean hasContent = !titleField.getValue().isBlank() && !instructionField.getValue().isBlank();
        saveButton.setEnabled(hasContent);
    }
}
