package com.wornux.ui.conversation;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Key;
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
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.spring.annotation.RouteScopeOwner;
import com.wornux.config.ChatProperties;
import com.wornux.services.crunner.CExamplePreparationService;
import com.wornux.services.crunner.CProgramDebugService;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.auth.NoAccessView;
import com.wornux.ui.components.chat.StudentQuestionPanel;
import com.wornux.ui.crunner.DebuggerPanel;
import jakarta.annotation.security.PermitAll;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Qualifier;

@Route(value = "chat", layout = MainLayout.class)
@PermitAll
public class ConversationView extends Composite<Div> implements BeforeEnterObserver {

    private final ConversationViewModel viewModel;
    private final CodeMessageList messageList;
    private final Div historyScroller;
    private final TextArea composerField;
    private final Button sendButton;
    private final Button debuggerToggleButton;
    private final StudentQuestionPanel questionPanel;
    private final DebuggerPanel debuggerPanel;
    private final SplitLayout splitLayout;
    private final ChatProperties chatProperties;
    private final CProgramDebugService cProgramDebugService;
    private final CExamplePreparationService cExamplePreparationService;
    private final Executor cRunnerExecutor;
    private final AuthenticatedAccountService authenticatedAccountService;
    private final WorkspaceRoutingService workspaceRoutingService;
    private boolean debuggerVisible;

    public ConversationView(
            @RouteScopeOwner(MainLayout.class) ConversationState state,
            @RouteScopeOwner(MainLayout.class) ConversationViewModel viewModel,
            ChatProperties chatProperties,
            CProgramDebugService cProgramDebugService,
            CExamplePreparationService cExamplePreparationService,
            @Qualifier("cRunnerExecutor") Executor cRunnerExecutor,
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService) {
        this.viewModel = viewModel;
        this.chatProperties = chatProperties;
        this.cProgramDebugService = cProgramDebugService;
        this.cExamplePreparationService = cExamplePreparationService;
        this.cRunnerExecutor = cRunnerExecutor;
        this.authenticatedAccountService = authenticatedAccountService;
        this.workspaceRoutingService = workspaceRoutingService;

        Div emptyState = createEmptyState(state);
        emptyState.bindVisible(state.emptyStateVisible());

        messageList = new CodeMessageList();
        messageList.setMarkdown(true);
        messageList.setThinkingSpinner(chatProperties.getUi().getThinkingSpinner());
        messageList.addDebugCodeRequestListener(event -> handleDebugCodeRequest(event.getCode(), event.getLang()));
        messageList.setWidthFull();
        Signal.effect(
            messageList,
            () -> messageList.setItems(
                state.messages().get().stream().map(messageSignal -> toMessageListItem(messageSignal.get())).toList()));

        var conversationStack = new Div(emptyState, messageList);
        ConversationCss.THREAD.addTo(conversationStack);

        historyScroller = new Div(conversationStack);
        historyScroller.setSizeFull();
        ConversationCss.SCROLL_REGION.addTo(historyScroller);
        historyScroller.addAttachListener(_ -> initializeAutoScrollTracking());

        composerField = new TextArea();
        composerField.setWidthFull();
        composerField.setPlaceholder("Escribe tu mensaje aquí...");
        composerField.setAriaLabel("Escribe tu mensaje aquí");
        ConversationCss.COMPOSER_INPUT.addTo(composerField);
        composerField.bindValue(state.composerText(), state.composerText()::set);
        composerField.bindEnabled(state.composerEnabled());
        composerField.setValueChangeMode(ValueChangeMode.EAGER);

        sendButton = new Button(new Icon(VaadinIcon.ARROW_UP));
        ConversationCss.COMPOSER_SEND_BUTTON.addTo(sendButton);
        sendButton.setAriaLabel("Enviar mensaje");
        sendButton.bindEnabled(state.sendEnabled());
        sendButton.addClickShortcut(Key.ENTER).listenOn(composerField);
        sendButton.addClickListener(_ -> submitPrompt());

        debuggerToggleButton = createDebuggerToggleButton();

        questionPanel = new StudentQuestionPanel();
        questionPanel.setSubmitHandler(viewModel::onSubmitInteractiveQuestionResponse);
        Signal.effect(questionPanel, () -> questionPanel.setQuestionSet(state.pendingQuestionSet().get()));
        Signal.effect(questionPanel, () -> questionPanel.setSubmitting(state.questionSubmissionInProgress().get()));

        var root = getContent();
        root.setSizeFull();
        ConversationCss.VIEW.addTo(root);

        var chatPane = new Div(
                debuggerToggleButton,
                historyScroller,
                createUsageBadge(state),
                createInputShell(state));
        chatPane.setSizeFull();
        ConversationCss.PANE.addTo(chatPane);

        debuggerPanel = new DebuggerPanel(cProgramDebugService, cExamplePreparationService, cRunnerExecutor);
        debuggerPanel.setSizeFull();
        debuggerPanel.setCloseHandler(() -> setDebuggerVisible(false));

        splitLayout = new SplitLayout(chatPane, debuggerPanel);
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(58);
        ConversationCss.DEBUG_SPLIT.addTo(splitLayout);
        splitLayout.addAttachListener(_ -> installResponsiveSplitBehavior(splitLayout));
        setDebuggerVisible(false);

        root.add(splitLayout);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedAccountService.requireCurrentAccount();
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
        var draftRequested = event.getLocation()
                .getQueryParameters()
                .getSingleParameter(ConversationViewModel.DRAFT_QUERY_PARAMETER)
                .filter(ConversationViewModel.DRAFT_QUERY_VALUE::equals)
                .isPresent();
        var requestedConversationParam = event.getLocation()
                .getQueryParameters()
                .getSingleParameter(ConversationViewModel.CONVERSATION_QUERY_PARAMETER)
                .orElse(null);

        var initialization = viewModel.initializeFromRoute(requestedConversationParam, draftRequested);
        if (initialization.rerouteRequired()) {
            rerouteToResolvedConversation(event, initialization.rerouteConversationId());
            return;
        }
        historyScroller.getElement().executeJs("this.scrollTop = 0;");
    }

    private Div createEmptyState(ConversationState chatState) {
        var emptyState = new Div();
        ConversationCss.EMPTY.addTo(emptyState);

        var animation = new AsciiFrameAnimation("crow3-frames", 240, 30);
        ConversationCss.EMPTY_ILLUSTRATION.addTo(animation);

        var animationFrame = new Div(animation);
        ConversationCss.EMPTY_FRAME.addTo(animationFrame);

        var title = new H2("Ask your first question");
        ConversationCss.EMPTY_TITLE.addTo(title);

        var description = new Paragraph(
                "Write a prompt and the tutor will help you reason step by step, clarify concepts, and practice with examples.");
        ConversationCss.EMPTY_DESCRIPTION.addTo(description);
        com.vaadin.flow.signals.Signal.effect(
            title,
            () -> title.setText(
                Boolean.TRUE.equals(chatState.setupRequired().get())
                        ? "Academic setup required"
                        : "Ask your first question"));
        com.vaadin.flow.signals.Signal.effect(
            description,
            () -> description.setText(
                Boolean.TRUE.equals(chatState.setupRequired().get())
                        ? chatState.setupMessage().get()
                        : "Write a prompt and the tutor will help you reason step by step, clarify concepts, and practice with examples."));

        var contentRow = getContentRow(title, description, animationFrame);

        emptyState.add(contentRow);
        return emptyState;
    }

    private static @NonNull HorizontalLayout getContentRow(H2 title, Paragraph description, Div animationFrame) {
        var textColumn = new VerticalLayout(title, description);
        ConversationCss.EMPTY_CONTENT.addTo(textColumn);
        textColumn.setPadding(false);
        textColumn.setSpacing(false);
        textColumn.setMargin(false);

        var contentRow = new HorizontalLayout(textColumn, animationFrame);
        ConversationCss.EMPTY_LAYOUT.addTo(contentRow);
        contentRow.setPadding(false);
        contentRow.setSpacing(false);
        contentRow.setMargin(false);
        contentRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        return contentRow;
    }

    private Div createInputShell(ConversationState state) {
        var composer = new Div(composerField, sendButton);
        ConversationCss.COMPOSER_FIELD_WRAP.addTo(composer);
        Signal.effect(composer, () -> composer.setVisible(!state.questionPanelVisible().get()));
        Signal.effect(questionPanel, () -> questionPanel.setVisible(state.questionPanelVisible().get()));

        var inputShell = new Div(questionPanel, composer);
        ConversationCss.COMPOSER.addTo(inputShell);
        return inputShell;
    }

    private Div createUsageBadge(ConversationState state) {
        var usageText = new Span();
        ConversationCss.USAGE_TEXT.addTo(usageText);

        var lineageText = new Span();
        ConversationCss.USAGE_LINEAGE.addTo(lineageText);

        var usageCopy = new Div(usageText, lineageText);
        ConversationCss.USAGE_COPY.addTo(usageCopy);

        var helpButton = new Button(new Icon(VaadinIcon.INFO_CIRCLE_O));
        helpButton.addThemeVariants(ButtonVariant.TERTIARY);
        ConversationCss.USAGE_HELP_BUTTON.addTo(helpButton);
        helpButton.setAriaLabel("Explicar uso del contexto");

        var helpPopover = new Popover();
        helpPopover.setTarget(helpButton);
        helpPopover.setModal(false);
        helpPopover.addClassName("chat-sidebar-help-popover");

        var helpTitle = new Span("Uso del contexto");
        helpTitle.addClassName("chat-sidebar-help-title");

        var helpCopy = new Paragraph(
                "Muestra los prompt tokens del contexto activo y el porcentaje usado contra el umbral de compactación configurado para resumir la conversación antes de perder calidad.");
        helpCopy.addClassName("chat-sidebar-help-description");
        helpPopover.add(new Div(helpTitle, helpCopy));

        var usageBadge = new Div(usageCopy, helpButton);
        ConversationCss.USAGE.addTo(usageBadge);
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
        var submitted = viewModel.onSubmitPrompt(
            this::scrollConversationToBottomSmoothIfEnabled,
            this::scrollConversationToBottomSmoothIfEnabled);
        if (submitted) {
            enableAutoScrollAndJumpToBottom();
        }
    }

    private Button createDebuggerToggleButton() {
        var button = new Button(new Icon(VaadinIcon.ANGLE_LEFT));
        button.addThemeVariants(ButtonVariant.TERTIARY);
        ConversationCss.DEBUGGER_TOGGLE.addTo(button);
        button.setAriaLabel("Open debugger");
        button.getElement().setAttribute("title", "Open debugger");
        button.addClickListener(_ -> setDebuggerVisible(!debuggerVisible));
        return button;
    }

    private void handleDebugCodeRequest(String code, String lang) {
        setDebuggerVisible(true);
        debuggerPanel.prepareAndDebugAssistantExample(code, lang);
    }

    private void setDebuggerVisible(boolean visible) {
        debuggerVisible = visible;
        debuggerPanel.setVisible(visible);
        splitLayout.setSplitterPosition(visible ? 58 : 100);
        ConversationCss.DEBUG_SPLIT_COLLAPSED.addTo(splitLayout, !visible);
        splitLayout.getElement()
                .executeJs(
                    "const mobile = window.matchMedia('(max-width: 960px)').matches; this.splitterPosition = $0 ? (mobile ? 42 : 58) : 100;",
                    visible);
        debuggerToggleButton.setAriaLabel(visible ? "Hide debugger" : "Open debugger");
        debuggerToggleButton.getElement().setAttribute("title", visible ? "Hide debugger" : "Open debugger");
        debuggerToggleButton.setVisible(!visible);
    }

    private CodeMessageListItem toMessageListItem(MessageState message) {
        var isUserMessage = message.role() == MessageType.USER;
        var item = new CodeMessageListItem(message.content(),
                message.createdAt(),
                isUserMessage ? "You" : "Socratic Tutor");
        item.setUserColorIndex();
        (isUserMessage ? ConversationCss.MESSAGE_USER : ConversationCss.MESSAGE_ASSISTANT).addTo(item);
        if (message.loading()) {
            ConversationCss.MESSAGE_LOADING.addTo(item);
        }
        return item;
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

    private void initializeAutoScrollTracking() {
        historyScroller.getElement()
                .executeJs("""
                           if (this.__autoScrollInitialized) {
                             return;
                           }
                           this.__autoScrollInitialized = true;
                           this.__autoScrollEnabled = true;
                           this.__autoScrollThreshold = 64;
                           this.__programmaticScroll = false;

                           const updateAutoScrollState = () => {
                             const distanceToBottom = this.scrollHeight - this.scrollTop - this.clientHeight;
                             const nearBottom = distanceToBottom <= this.__autoScrollThreshold;
                             if (nearBottom) {
                               this.__autoScrollEnabled = true;
                               return;
                             }
                             if (!this.__programmaticScroll) {
                               this.__autoScrollEnabled = false;
                             }
                           };

                           this.addEventListener('scroll', () => {
                             updateAutoScrollState();
                           }, { passive: true });

                           this.__updateAutoScrollState = updateAutoScrollState;
                           updateAutoScrollState();
                           """);
    }

    private void enableAutoScrollAndJumpToBottom() {
        historyScroller.getElement().executeJs("""
                                               this.__autoScrollEnabled = true;
                                               this.__programmaticScroll = true;
                                               this.scrollTo({ top: this.scrollHeight, behavior: 'auto' });
                                               requestAnimationFrame(() => {
                                                 this.__programmaticScroll = false;
                                                 this.__updateAutoScrollState?.();
                                               });
                                               """);
    }

    private void scrollConversationToBottomSmoothIfEnabled() {
        historyScroller.getElement().executeJs("""
                                               if (!this.__autoScrollEnabled) {
                                                 return;
                                               }
                                               this.__programmaticScroll = true;
                                               this.scrollTo({ top: this.scrollHeight, behavior: 'smooth' });
                                               requestAnimationFrame(() => {
                                                 this.__programmaticScroll = false;
                                                 this.__updateAutoScrollState?.();
                                               });
                                               """);
    }

    private void installResponsiveSplitBehavior(SplitLayout splitLayout) {
        splitLayout.getElement()
                .executeJs(
                    """
                    if (this.__responsiveSplitInstalled) {
                      return;
                    }
                    this.__responsiveSplitInstalled = true;

                    const media = window.matchMedia('(max-width: 960px)');
                    const update = () => {
                      this.orientation = 'horizontal';
                      this.splitterPosition = this.classList.contains('conversation-view__debug-split--collapsed') ? 100 : (media.matches ? 42 : 58);
                    };
                    media.addEventListener?.('change', update);
                    media.addListener?.(update);
                    update();
                    """);
    }
}
