package com.wornux.chat;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.applayout.DrawerToggle;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.shared.Registration;
import com.wornux.MainLayout;
import com.wornux.chat.ui.StudentQuestionPanel;
import org.springframework.ai.chat.messages.MessageType;

import java.util.UUID;
import java.util.Locale;

@Route(value = "", layout = MainLayout.class)
public class ChatView extends Composite<Div> implements BeforeEnterObserver {

    private final ChatUiController controller;
    private final CodeMessageList messageList;
    private final Div historyScroller;
    private final TextArea composerField;
    private final Button sendButton;
    private final StudentQuestionPanel questionPanel;
    private transient Registration pollRegistration;

    public ChatView(ChatUiState state, ChatUiController controller, ChatProperties chatProperties) {
        this.controller = controller;

        Div emptyState = createEmptyState();
        emptyState.bindVisible(state.emptyStateVisible());

        messageList = new CodeMessageList();
        messageList.setMarkdown(true);
        messageList.setThinkingSpinner(chatProperties.getUi().getThinkingSpinner());
        messageList.setWidthFull();
        Signal.effect(messageList, () -> messageList.setItems(state.messages().get().stream()
                .map(messageSignal -> toMessageListItem(messageSignal.get()))
                .toList()));

        var conversationStack = new Div(emptyState, messageList);
        conversationStack.addClassName("chat-thread");

        historyScroller = new Div(conversationStack);
        historyScroller.setSizeFull();
        historyScroller.addClassName("chat-scroll-region");
        historyScroller.addAttachListener(_ -> initializeAutoScrollTracking());

        var floatingDrawerToggle = new DrawerToggle();
        floatingDrawerToggle.addThemeVariants(ButtonVariant.TERTIARY);
        floatingDrawerToggle.addClassName("shell-drawer-toggle");

        composerField = new TextArea();
        composerField.setWidthFull();
        composerField.setPlaceholder("Escribe tu mensaje aquí...");
        composerField.setAriaLabel("Escribe tu mensaje aquí");
        composerField.addClassName("chat-composer-input");
        composerField.bindValue(state.composerText(), state.composerText()::set);
        composerField.bindEnabled(state.composerEnabled());
        composerField.setValueChangeMode(ValueChangeMode.EAGER);

        sendButton = new Button(new Icon(VaadinIcon.ARROW_UP));
        sendButton.addClassName("chat-composer-send");
        sendButton.setAriaLabel("Enviar mensaje");
        sendButton.bindEnabled(state.sendEnabled());
        sendButton.addClickShortcut(Key.ENTER).listenOn(composerField);
        sendButton.addClickListener(_ -> submitPrompt());

        questionPanel = new StudentQuestionPanel();
        questionPanel.setSubmitHandler(controller::submitInteractiveQuestionResponse);
        Signal.effect(questionPanel, () -> questionPanel.setQuestionSet(state.pendingQuestionSet().get()));
        Signal.effect(questionPanel, () -> questionPanel.setSubmitting(state.questionSubmissionInProgress().get()));

        var root = getContent();
        root.setSizeFull();
        root.addClassName("chat-view");
        root.add(floatingDrawerToggle, historyScroller, createUsageBadge(state), createInputShell(state));
        root.addAttachListener(event -> {
            event.getUI().setPollInterval(500);
            if (pollRegistration == null) {
                pollRegistration = event.getUI().addPollListener(_ -> controller.syncPendingQuestionState());
            }
            controller.syncPendingQuestionState();
        });
        root.addDetachListener(event -> {
            if (pollRegistration != null) {
                pollRegistration.remove();
                pollRegistration = null;
            }
            event.getUI().setPollInterval(-1);
        });
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var draftRequested = event.getLocation()
                .getQueryParameters()
                .getSingleParameter(ChatUiController.DRAFT_QUERY_PARAMETER)
                .filter(ChatUiController.DRAFT_QUERY_VALUE::equals)
                .isPresent();
        var requestedConversationParam = event.getLocation()
                .getQueryParameters()
                .getSingleParameter(ChatUiController.CONVERSATION_QUERY_PARAMETER)
                .orElse(null);

        var initialization = controller.initializeFromRoute(requestedConversationParam, draftRequested);
        if (initialization.rerouteRequired()) {
            rerouteToResolvedConversation(event, initialization.rerouteConversationId());
            return;
        }
        historyScroller.getElement().executeJs("this.scrollTop = 0;");
    }

    private Div createEmptyState() {
        var state = new Div();
        state.addClassName("chat-empty");

        var animation = new AsciiFrameAnimation("crow3-frames", 240, 30);
        animation.addClassName("chat-empty-illustration");

        var animationFrame = new Div(animation);
        animationFrame.addClassName("chat-empty-frame");

        var eyebrow = new Span("Asistente académico");

        var title = new H2("Haz tu primera pregunta");
        title.addClassName("chat-empty-title");

        var description = new Paragraph("Escribe y te ayudare a razonar paso a paso, aclarar conceptos y practicar con ejemplos.");
        description.addClassName("chat-empty-description");

        var textColumn = new VerticalLayout(eyebrow, title, description);
        textColumn.addClassName("chat-empty-content");
        textColumn.setPadding(false);
        textColumn.setSpacing(false);
        textColumn.setMargin(false);

        var contentRow = new HorizontalLayout(textColumn, animationFrame);
        contentRow.addClassName("chat-empty-layout");
        contentRow.setPadding(false);
        contentRow.setSpacing(false);
        contentRow.setMargin(false);
        contentRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        state.add(contentRow);
        return state;
    }

    private Div createInputShell(ChatUiState state) {
        var compactionStatus = createCompactionStatus(state);

        var composer = new Div(composerField, sendButton);
        composer.addClassName("chat-composer-wrap");
        Signal.effect(composer, () -> composer.setVisible(!state.questionPanelVisible().get()));
        Signal.effect(questionPanel, () -> questionPanel.setVisible(state.questionPanelVisible().get()));

        var inputShell = new Div(compactionStatus, questionPanel, composer);
        inputShell.addClassName("chat-composer-shell");
        return inputShell;
    }

    private Div createUsageBadge(ChatUiState state) {
        var usageText = new Span();
        usageText.addClassName("chat-usage-text");

        var lineageText = new Span();
        lineageText.addClassName("chat-usage-lineage");

        var usageCopy = new Div(usageText, lineageText);
        usageCopy.addClassName("chat-usage-copy");

        var helpButton = new Button(new Icon(VaadinIcon.INFO_CIRCLE_O));
        helpButton.addThemeVariants(ButtonVariant.TERTIARY);
        helpButton.addClassName("chat-usage-help-button");
        helpButton.setAriaLabel("Explicar uso del contexto");

        var helpPopover = new Popover();
        helpPopover.setTarget(helpButton);
        helpPopover.setModal(false);
        helpPopover.addClassName("chat-sidebar-help-popover");

        var helpTitle = new Span("Uso del contexto");
        helpTitle.addClassName("chat-sidebar-help-title");

        var helpCopy = new Paragraph(
                "Muestra los prompt tokens del transcript activo y el porcentaje usado contra el umbral de compactacion configurado para resumir la conversacion antes de perder calidad.");
        helpCopy.addClassName("chat-sidebar-help-description");
        helpPopover.add(new Div(helpTitle, helpCopy));

        var usageBadge = new Div(usageCopy, helpButton);
        usageBadge.addClassName("chat-usage-badge");
        usageBadge.setVisible(false);

        Signal.effect(usageBadge, () -> {
            var inputTokens = state.usageInputTokens().get();
            var usagePercent = state.usagePercent().get();
            var compacted = Boolean.TRUE.equals(state.conversationCompacted().get());
            var level = state.compactionLevel().get();
            var sourceTranscriptId = state.compactedFromTranscriptId().get();
            var visible = (inputTokens != null && usagePercent != null) || compacted;
            usageBadge.setVisible(visible);
            if (inputTokens != null && usagePercent != null) {
                usageText.setText("%s (%d%%)".formatted(formatTokenCount(inputTokens), usagePercent));
            } else {
                usageText.setText("Contexto compactado");
            }

            if (compacted && level != null) {
                var sourceLabel = sourceTranscriptId == null ? "" : " · desde %s".formatted(shortId(sourceTranscriptId));
                lineageText.setText("Compactado · nivel %d%s".formatted(level, sourceLabel));
                lineageText.setVisible(true);
            } else {
                lineageText.setText("");
                lineageText.setVisible(false);
            }
        });

        return usageBadge;
    }

    private Div createCompactionStatus(ChatUiState state) {
        var spinner = new BrailleSpinner();
        spinner.addClassName("chat-compaction-spinner");
        spinner.setSpinner("fillsweep");

        var label = new Span();
        label.addClassName("chat-compaction-label");

        var status = new Div(spinner, label);
        status.addClassName("chat-compaction-status");
        status.setVisible(false);

        Signal.effect(status, () -> {
            var compacting = Boolean.TRUE.equals(state.compactionInProgress().get());
            status.setVisible(compacting);
            label.setText(state.compactionLabel().get());
        });

        return status;
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

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private void submitPrompt() {
        var submitted = controller.submitPrompt(
                this::scrollConversationToBottomSmoothIfEnabled,
                this::scrollConversationToBottomSmoothIfEnabled
        );
        if (submitted) {
            enableAutoScrollAndJumpToBottom();
        }
    }

    private CodeMessageListItem toMessageListItem(MessageVm message) {
        var isUserMessage = message.role() == MessageType.USER;
        var item = new CodeMessageListItem(
                message.content(),
                message.createdAt(),
                isUserMessage ? "You" : "Socratic Tutor"
        );
        item.setUserColorIndex(isUserMessage ? 0 : 1);
        item.addClassNames(isUserMessage ? "chat-message-user" : "chat-message-assistant");
        if (message.loading()) {
            item.addClassNames("is-loading");
        }
        return item;
    }

    private void rerouteToResolvedConversation(BeforeEnterEvent event, UUID resolvedConversationId) {
        if (resolvedConversationId == null) {
            event.rerouteTo(ChatView.class, QueryParameters.empty());
            return;
        }
        event.rerouteTo(ChatView.class, QueryParameters.of(ChatUiController.CONVERSATION_QUERY_PARAMETER, resolvedConversationId.toString()));
    }

    private void initializeAutoScrollTracking() {
        historyScroller.getElement().executeJs("""
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
}
