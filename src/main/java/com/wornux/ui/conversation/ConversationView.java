package com.wornux.ui.conversation;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.wornux.config.ChatProperties;
import com.wornux.dtos.chat.questions.StudentQuestionSet;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.chat.ChatSessionActivity;
import com.wornux.services.chat.ModelAvailabilityService;
import com.wornux.services.crunner.CExamplePreparationService;
import com.wornux.services.crunner.CProgramDebugService;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.auth.NoAccessView;
import com.wornux.ui.components.chat.StudentQuestionPanel;
import com.wornux.ui.crunner.DebuggerPanel;
import com.wornux.ui.css.UiCss;
import jakarta.annotation.security.PermitAll;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Qualifier;

@Route(value = "chat", layout = MainLayout.class)
@PermitAll
@RequiresPermission(AppPermission.CONVERSATION_VIEW)
public class ConversationView extends Composite<Div> implements BeforeEnterObserver {

    private final ConversationViewModel viewModel;
    private final MessagesList messageList;
    private final ConversationComposer composer;
    private final Button debuggerToggleButton;
    private final StudentQuestionPanel questionPanel;
    private final DebuggerPanel debuggerPanel;
    private final ConversationDebugSplit debugSplit;
    private final transient AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final transient WorkspaceRoutingService workspaceRoutingService;
    private transient AutoCloseable modelAvailabilitySubscription;
    private boolean debuggerVisible;

    public ConversationView(
            @RouteScopeOwner(MainLayout.class) ConversationState state,
            @RouteScopeOwner(MainLayout.class) ConversationViewModel viewModel,
            ChatProperties chatProperties,
            CProgramDebugService cProgramDebugService,
            CExamplePreparationService cExamplePreparationService,
            @Qualifier("cRunnerExecutor") Executor cRunnerExecutor,
            AuthenticatedUserContextUtils authenticatedUserContextUtils,
            WorkspaceRoutingService workspaceRoutingService,
            ModelAvailabilityService modelAvailabilityService) {
        this.viewModel = viewModel;
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
        this.workspaceRoutingService = workspaceRoutingService;
        bindModelAvailability(state, modelAvailabilityService);
        this.viewModel.bindTurnUiAnchor(this);

        Div emptyState = createEmptyState(state);
        emptyState.bindVisible(state.emptyStateVisible());

        messageList = new MessagesList();
        messageList.setThinkingSpinner(chatProperties.getUi().getThinkingSpinner());
        messageList.addDebugCodeRequestListener(event -> handleDebugCodeRequest(event.getCode(), event.getLang()));
        messageList.setWidthFull();
        Signal.effect(
            messageList,
            () -> messageList.setItems(
                state.messages().get().stream().map(messageSignal -> toMessageListItem(messageSignal.get())).toList()));

        var conversationStack = new Div(emptyState, messageList);
        UiCss.CONVERSATION_THREAD.addTo(conversationStack);

        var historyScroller = new Div(conversationStack);
        historyScroller.setSizeFull();
        UiCss.CONVERSATION_SCROLL_REGION.addTo(historyScroller);

        composer = new ConversationComposer(state, chatProperties.composerPromptLimit(), this::submitPrompt);

        debuggerToggleButton = createDebuggerToggleButton();

        questionPanel = new StudentQuestionPanel();
        questionPanel.setSubmitHandler(viewModel::onSubmitInteractiveQuestionResponse);
        Signal.effect(questionPanel, () -> questionPanel.setQuestionSet(currentQuestionSetForUi(state)));
        Signal.effect(questionPanel, () -> questionPanel.setSubmitting(state.questionSubmissionInProgress().get()));

        var root = getContent();
        root.setSizeFull();
        UiCss.CONVERSATION_VIEW.addTo(root);

        var chatPane = new Div(debuggerToggleButton, historyScroller, createUsageBadge(state), createInputShell(state));
        chatPane.setSizeFull();
        UiCss.CONVERSATION_PANE.addTo(chatPane);

        debuggerPanel = new DebuggerPanel(cProgramDebugService, cExamplePreparationService, cRunnerExecutor);
        debuggerPanel.setSizeFull();
        debuggerPanel.setCloseHandler(() -> setDebuggerVisible(false));

        debugSplit = new ConversationDebugSplit(chatPane, debuggerPanel);
        setDebuggerVisible(false);

        root.add(debugSplit);
    }

    private void bindModelAvailability(ConversationState state, ModelAvailabilityService modelAvailabilityService) {
        state.modelAvailabilityStatus().set(modelAvailabilityService.currentStatus());
        getContent().addAttachListener(event -> {
            closeModelAvailabilitySubscription();
            var ui = event.getUI();
            modelAvailabilitySubscription = modelAvailabilityService.subscribe(status -> {
                if (!getContent().isAttached()) {
                    return;
                }
                ui.access(() -> state.modelAvailabilityStatus().set(status));
            });
        });
        getContent().addDetachListener(_ -> closeModelAvailabilitySubscription());
    }

    private void closeModelAvailabilitySubscription() {
        if (modelAvailabilitySubscription == null) {
            return;
        }
        try {
            modelAvailabilitySubscription.close();
        }
        catch (Exception _) {
            // Nothing to do: the status listener is best-effort UI state.
        }
        modelAvailabilitySubscription = null;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedUserContextUtils.requireCurrentAccount();
        var hasProfessorAccess = workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.PROFESSOR);
        var hasStudentAccess = workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.STUDENT);
        if (!hasProfessorAccess && !hasStudentAccess) {
            event.forwardTo(NoAccessView.class);
            return;
        }
        if (!workspaceRoutingService.currentClassMembership(account, null).isPresent()) {
            if (hasProfessorAccess) {
                workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR);
            }
            else {
                workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.STUDENT);
            }
        }
        var requestedConversationParam = event.getLocation()
                .getQueryParameters()
                .getSingleParameter(ConversationViewModel.CONVERSATION_QUERY_PARAMETER)
                .orElse(null);

        var initialization = viewModel.initializeFromRoute(requestedConversationParam, event.isRefreshEvent());
        if (initialization.rerouteRequired()) {
            rerouteToResolvedConversation(event, initialization.rerouteConversationId());
        }
    }

    private Div createEmptyState(ConversationState chatState) {
        var emptyState = new Div();
        UiCss.CONVERSATION_EMPTY.addTo(emptyState);

        var animation = new AsciiFrameAnimation("crow3-frames", 240, 30);
        UiCss.CONVERSATION_EMPTY_ILLUSTRATION.addTo(animation);

        var animationFrame = new Div(animation);
        UiCss.CONVERSATION_EMPTY_FRAME.addTo(animationFrame);

        var title = new H2();
        UiCss.CONVERSATION_EMPTY_TITLE.addTo(title);
        setEmptyStateTitle(title, false);

        var description = new Paragraph(
                "Escribe una pregunta y el tutor te ayudará a razonar paso a paso, aclarar conceptos y practicar con ejemplos.");
        UiCss.CONVERSATION_EMPTY_DESCRIPTION.addTo(description);
        com.vaadin.flow.signals.Signal.effect(
            title,
            () -> setEmptyStateTitle(title, Boolean.TRUE.equals(chatState.setupRequired().get())));
        com.vaadin.flow.signals.Signal.effect(
            description,
            () -> description.setText(
                Boolean.TRUE.equals(chatState.setupRequired().get())
                        ? chatState.setupMessage().get()
                        : "Escribe una pregunta y el tutor te ayudará a razonar paso a paso, aclarar conceptos y practicar con ejemplos."));

        var contentRow = getContentRow(title, description, animationFrame);

        emptyState.add(contentRow);
        return emptyState;
    }

    private static void setEmptyStateTitle(H2 title, boolean setupRequired) {
        title.removeAll();
        if (setupRequired) {
            title.add(new Text("Configuración "), italicTitleWord("académica"), new Text(" requerida"));
            return;
        }
        title.add(italicTitleWord("Haz"), new Text(" tu primera pregunta"));
    }

    private static Span italicTitleWord(String text) {
        var word = new Span(text);
        UiCss.CONVERSATION_EMPTY_TITLE_ITALIC.addTo(word);
        return word;
    }

    private static @NonNull HorizontalLayout getContentRow(H2 title, Paragraph description, Div animationFrame) {
        var textColumn = new VerticalLayout(title, description);
        UiCss.CONVERSATION_EMPTY_CONTENT.addTo(textColumn);
        textColumn.setPadding(false);
        textColumn.setSpacing(false);
        textColumn.setMargin(false);

        var contentRow = new HorizontalLayout(textColumn, animationFrame);
        UiCss.CONVERSATION_EMPTY_LAYOUT.addTo(contentRow);
        contentRow.setPadding(false);
        contentRow.setSpacing(false);
        contentRow.setMargin(false);
        contentRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        return contentRow;
    }

    private Div createInputShell(ConversationState state) {
        var inputShell = new Div();
        UiCss.CONVERSATION_COMPOSER.addTo(inputShell);
        var activityBlocker = createActivityBlocker(state);

        Signal.effect(inputShell, () -> showCurrentInput(inputShell, state, activityBlocker));

        return inputShell;
    }

    private void showCurrentInput(Div inputShell, ConversationState state, Div activityBlocker) {
        inputShell.removeAll();
        var currentQuestionSet = currentQuestionSetForUi(state);
        inputShell.getElement()
                .getClassList()
                .set(UiCss.CONVERSATION_COMPOSER_QUESTION_MODE.value(), currentQuestionSet != null);
        if (currentQuestionSet != null) {
            inputShell.add(questionPanel);
            return;
        }
        inputShell.add(isComposerAvailable(state) ? composer : activityBlocker);
    }

    private static StudentQuestionSet currentQuestionSetForUi(ConversationState state) {
        return state.pendingQuestionSet().get();
    }

    private boolean isComposerAvailable(ConversationState state) {
        return state.activity().get() == ChatSessionActivity.IDLE;
    }

    private Div createActivityBlocker(ConversationState state) {
        var spinner = createFillSweepSpinner();
        var title = createActivityTitle();
        var description = createActivityDescription();
        var blocker = createActivityBlockerShell(spinner, title, description);

        Signal.effect(blocker, () -> describeActivity(state.activity().get(), title, description));

        return blocker;
    }

    private BrailleSpinner createFillSweepSpinner() {
        var spinner = new BrailleSpinner("fillsweep");
        UiCss.CONVERSATION_ACTIVITY_SPINNER.addTo(spinner);
        return spinner;
    }

    private Span createActivityTitle() {
        var title = new Span();
        UiCss.CONVERSATION_ACTIVITY_TITLE.addTo(title);
        return title;
    }

    private Span createActivityDescription() {
        var description = new Span();
        UiCss.CONVERSATION_ACTIVITY_DESCRIPTION.addTo(description);
        return description;
    }

    private Div createActivityBlockerShell(BrailleSpinner spinner, Span title, Span description) {
        var copy = new Div(title, description);
        UiCss.CONVERSATION_ACTIVITY_COPY.addTo(copy);

        var blocker = new Div(spinner, copy);
        UiCss.CONVERSATION_ACTIVITY_BLOCKER.addTo(blocker);
        blocker.getElement().setAttribute("aria-live", "polite");
        blocker.getElement().setAttribute("aria-busy", "true");
        return blocker;
    }

    private void describeActivity(ChatSessionActivity activity, Span title, Span description) {
        if (activity == ChatSessionActivity.COMPACTING) {
            title.setText("Compactando el contexto");
            description.setText("Resumiendo el historial para mantener la conversación precisa.");
            return;
        }
        title.setText("Generando respuesta");
        description.setText("El tutor está razonando; el compositor se habilitará al terminar.");
    }

    private Div createUsageBadge(ConversationState state) {
        var usageText = new Span();
        UiCss.CONVERSATION_USAGE_TEXT.addTo(usageText);

        var lineageText = new Span();
        UiCss.CONVERSATION_USAGE_LINEAGE.addTo(lineageText);

        var usageCopy = new Div(usageText, lineageText);
        UiCss.CONVERSATION_USAGE_COPY.addTo(usageCopy);

        var helpButton = new Button(new Icon(VaadinIcon.INFO_CIRCLE_O));
        helpButton.addThemeVariants(ButtonVariant.TERTIARY);
        UiCss.CONVERSATION_USAGE_HELP_BUTTON.addTo(helpButton);
        helpButton.setAriaLabel("Explicar uso del contexto");

        var helpPopover = new Popover();
        helpPopover.setTarget(helpButton);
        helpPopover.setModal(false);
        UiCss.USAGE_HELP_POPOVER.addTo(helpPopover);

        var helpTitle = new Span("Uso del contexto");
        UiCss.USAGE_HELP_POPOVER_TITLE.addTo(helpTitle);

        var helpCopy = new Paragraph(
                "Muestra los tokens de entrada del contexto activo y el porcentaje usado respecto al umbral de compactación configurado para resumir la conversación antes de perder calidad.");
        UiCss.USAGE_HELP_POPOVER_DESCRIPTION.addTo(helpCopy);
        helpPopover.add(new Div(helpTitle, helpCopy));

        var usageBadge = new Div(usageCopy, helpButton);
        UiCss.CONVERSATION_USAGE.addTo(usageBadge);
        usageBadge.setVisible(false);

        Signal.effect(usageBadge, () -> {
            var inputTokens = state.usageInputTokens().get();
            var usagePercent = state.usagePercent().get();
            var compacted = Boolean.TRUE.equals(state.conversationCompacted().get());
            var visible = (inputTokens != null && usagePercent != null) || compacted;
            usageBadge.setVisible(visible);
            if (inputTokens != null && usagePercent != null) {
                usageText.setText("%s (%d%%)".formatted(formatTokenCount(inputTokens), usagePercent));
            }
            else {
                usageText.setText("Contexto compactado");
            }

            if (compacted) {
                lineageText.setText("Historial resumido para el contexto activo");
                lineageText.setVisible(true);
            }
            else {
                lineageText.setText("");
                lineageText.setVisible(false);
            }
        });

        return usageBadge;
    }

    private String formatTokenCount(int tokens) {
        if (tokens >= 1_000_000) {
            return compact(tokens / 1_000_000d) + "M";
        }
        if (tokens >= 1_000) {
            return compact(tokens / 1_000d) + "K";
        }
        return Integer.toString(tokens);
    }

    private String compact(double value) {
        var rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return Integer.toString((int) rounded);
        }
        return String.format(Locale.US, "%.1f", rounded);
    }

    private void submitPrompt() {
        viewModel.onSubmitPrompt();
    }

    private Button createDebuggerToggleButton() {
        var button = new Button(new Icon(VaadinIcon.ANGLE_LEFT));
        button.addThemeVariants(ButtonVariant.TERTIARY);
        UiCss.CONVERSATION_DEBUGGER_TOGGLE.addTo(button);
        button.setAriaLabel("Abrir depurador");
        button.getElement().setAttribute("title", "Abrir depurador");
        button.addClickListener(_ -> setDebuggerVisible(!debuggerVisible));
        return button;
    }

    private void handleDebugCodeRequest(String code, String lang) {
        setDebuggerVisible(true);
        debuggerPanel.prepareAndDebugAssistantExample(code, lang);
    }

    private void setDebuggerVisible(boolean visible) {
        debuggerVisible = visible;
        debugSplit.setDebuggerVisible(visible);
        debuggerToggleButton.setAriaLabel(visible ? "Ocultar depurador" : "Abrir depurador");
        debuggerToggleButton.getElement().setAttribute("title", visible ? "Ocultar depurador" : "Abrir depurador");
        UiCss.CONVERSATION_DEBUGGER_TOGGLE_HIDDEN.addTo(debuggerToggleButton, visible);
    }

    private MessageItem toMessageListItem(MessageState message) {
        var isUserMessage = message.role() == MessageType.USER;
        return new MessageItem(message.content(),
                message.createdAt(),
                isUserMessage ? "Tú" : "Tutor Socrático",
                isUserMessage ? MessageItem.Variant.USER : MessageItem.Variant.ASSISTANT,
                message.loading());
    }

    private void rerouteToResolvedConversation(BeforeEnterEvent event, UUID resolvedConversationId) {
        if (resolvedConversationId == null) {
            event.rerouteTo(ConversationView.class, QueryParameters.empty());
            return;
        }
        event.rerouteTo(
            ConversationView.class,
            QueryParameters.of(ConversationViewModel.CONVERSATION_QUERY_PARAMETER, resolvedConversationId.toString()));
    }

}
