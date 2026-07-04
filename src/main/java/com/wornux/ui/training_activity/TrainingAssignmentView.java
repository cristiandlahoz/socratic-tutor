package com.wornux.ui.training_activity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.wornux.config.ChatProperties;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.entities.training_activity.SafeBrowserEventType;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingAssignmentEvaluationService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.conversation.CodeMessageList;
import com.wornux.ui.conversation.CodeMessageListItem;
import com.wornux.ui.conversation.ConversationComposer;
import com.wornux.ui.css.UiCss;
import jakarta.annotation.security.PermitAll;

@Route(value = "training-activity/assignments", layout = MainLayout.class)
@PermitAll
@RequiresPermission(AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW)
public class TrainingAssignmentView extends Composite<Div> implements HasUrlParameter<String> {

    private static final String ASSIGNMENT_SHELL_CLASS = "assignment-shell-hidden";
    private static final String TUTOR_NAME = "Tutor Socrático";
    private static final String STUDENT_NAME = "Tú";
    private static final String ANSWER_PLACEHOLDER = "Escribe tu respuesta aquí...";
    private static final String SUBMITTED_PLACEHOLDER = "Actividad finalizada";
    private static final String SUBMITTED_MESSAGE =
            "La actividad formativa ha finalizado. Tu profesor ya puede revisar el reporte.";
    private static final String LOCKED_MESSAGE =
            "Safe Browser Mode fue interrumpido. Tu profesor debe revisar o desbloquear esta asignación.";

    private final TrainingAssignmentEvaluationService evaluationService;
    private final SafeBrowserModeService safeBrowserModeService;
    private final SafeBrowserAssignmentStateBus assignmentStateBus;
    private final CodeMessageList messageList = new CodeMessageList();
    private final ConversationComposer composer;
    private final Div safeBrowserEntry = new Div();
    private final Div inputShell = new Div();
    private UUID assignmentId;
    private TrainingActivityAssignment assignment;
    private AutoCloseable assignmentStateSubscription;

    public TrainingAssignmentView(
            TrainingAssignmentEvaluationService evaluationService,
            SafeBrowserModeService safeBrowserModeService,
            SafeBrowserAssignmentStateBus assignmentStateBus,
            ChatProperties chatProperties) {
        this.evaluationService = evaluationService;
        this.safeBrowserModeService = safeBrowserModeService;
        this.assignmentStateBus = assignmentStateBus;

        messageList.setMarkdown(true);
        messageList.setThinkingSpinner(chatProperties.getUi().getThinkingSpinner());
        messageList.setWidthFull();

        composer = new ConversationComposer(
                ANSWER_PLACEHOLDER,
                "Escribe tu respuesta aquí",
                "Enviar respuesta",
                this::submitAnswer);
        composer.addValueChangeListener(this::updateComposerState);
        composer.setSubmitEnabled(false);

        UiCss.CONVERSATION_COMPOSER.addTo(inputShell);
        inputShell.add(composer);

        var conversationStack = new Div(messageList);
        UiCss.CONVERSATION_THREAD.addTo(conversationStack);

        var historyScroller = new Div(conversationStack);
        historyScroller.setSizeFull();
        UiCss.CONVERSATION_SCROLL_REGION.addTo(historyScroller);

        var chatPane = new Div(safeBrowserEntry, historyScroller, inputShell);
        chatPane.setSizeFull();
        UiCss.CONVERSATION_PANE.addTo(chatPane);

        var content = getContent();
        content.setSizeFull();
        UiCss.CONVERSATION_VIEW.addTo(content);
        content.add(chatPane);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        setAssignmentShellHidden(true);
        subscribeToAssignmentStateChanges(attachEvent.getUI());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        unsubscribeFromAssignmentStateChanges();
        setAssignmentShellHidden(false);
        super.onDetach(detachEvent);
    }

    private void subscribeToAssignmentStateChanges(UI ui) {
        unsubscribeFromAssignmentStateChanges();
        assignmentStateSubscription = assignmentStateBus.subscribe(notification -> {
            if (!notification.affectsAssignment(assignmentId)) {
                return;
            }
            ui.access(() -> {
                assignment = evaluationService.getForCurrentStudent(assignmentId);
                renderAssignment();
            });
        });
    }

    private void unsubscribeFromAssignmentStateChanges() {
        if (assignmentStateSubscription == null) {
            return;
        }
        try {
            assignmentStateSubscription.close();
        }
        catch (Exception exception) {
            // Subscription removal has no checked failure path.
        }
        assignmentStateSubscription = null;
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        try {
            assignmentId = UUID.fromString(parameter);
            assignment = evaluationService.getForCurrentStudent(assignmentId);
            if (canAutoStart(assignment)) {
                assignment = evaluationService.start(assignmentId);
            }
            renderAssignment();
        }
        catch (IllegalArgumentException | SecurityException exception) {
            Notification.show(exception.getMessage());
            UI.getCurrent().navigate("student");
        }
    }

    private void renderAssignment() {
        setAssignmentShellHidden(assignment == null || assignment.getStatus() != TrainingActivityAssignmentStatus.SUBMITTED);
        renderSafeBrowserEntry();
        messageList.setItems(toMessages(assignment));
        if (isBlocked(assignment)) {
            inputShell.setVisible(true);
            composer.clear();
            composer.setPlaceholder("Asignación bloqueada");
            updateComposerState();
            return;
        }
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            inputShell.setVisible(true);
            composer.clear();
            composer.setPlaceholder(SUBMITTED_PLACEHOLDER);
            updateComposerState();
            return;
        }
        inputShell.setVisible(true);
        composer.setPlaceholder(ANSWER_PLACEHOLDER);
        composer.clear();
        updateComposerState();
    }

    private void submitAnswer() {
        if (assignmentId == null || composer.getValue().trim().isBlank()) {
            return;
        }
        var wasSubmitted = assignment != null && assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED;
        assignment = evaluationService.answer(assignmentId, composer.getValue());
        renderAssignment();
        if (!wasSubmitted && assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            showCompletionDialog();
        }
    }

    private boolean canAutoStart(TrainingActivityAssignment assignment) {
        return !assignment.getTrainingActivity().isSafeBrowserEnabled()
                && assignment.getStatus() != TrainingActivityAssignmentStatus.SUBMITTED;
    }

    private boolean isBlocked(TrainingActivityAssignment assignment) {
        return assignment.isSafeBrowserLocked()
                || assignment.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED;
    }

    private void setAssignmentShellHidden(boolean hidden) {
        getElement().executeJs(
                "this.closest('vaadin-app-layout')?.classList.toggle($0, $1)",
                ASSIGNMENT_SHELL_CLASS,
                hidden);
    }

    private void renderSafeBrowserEntry() {
        safeBrowserEntry.removeAll();
        safeBrowserEntry.setVisible(assignment != null && assignment.getTrainingActivity().isSafeBrowserEnabled());
        if (!safeBrowserEntry.isVisible()) {
            return;
        }
        safeBrowserEntry.addClassName("safe-browser-entry");
        if (assignment.isSafeBrowserLocked()) {
            safeBrowserEntry.add(new Paragraph(LOCKED_MESSAGE));
            return;
        }
        if (assignment.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED) {
            safeBrowserEntry.add(new Paragraph("La ventana de evaluación terminó."));
            return;
        }
        if (assignment.isSafeBrowserSessionActive()) {
            safeBrowserEntry.add(new Paragraph("Safe Browser Mode activo. Mantén esta pestaña visible y la pantalla completa."));
            return;
        }
        var instructions = new Paragraph(
                "Esta actividad requiere Safe Browser Mode. Al iniciar, mantén la pestaña visible y acepta pantalla completa.");
        var startButton = new Button("Start Safe Browser Mode", _ -> startSafeBrowserSession());
        startButton.addThemeVariants(ButtonVariant.PRIMARY);
        safeBrowserEntry.add(instructions, startButton);
    }

    private void startSafeBrowserSession() {
        try {
            assignment = safeBrowserModeService.startSession(assignmentId);
            assignment = evaluationService.start(assignmentId);
            renderAssignment();
            installSafeBrowserRuntime();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    private void installSafeBrowserRuntime() {
        getElement().executeJs("""
            const root = this;
            if (root.__safeBrowserInstalled) return;
            root.__safeBrowserInstalled = true;
            const report = (type) => root.$server.reportSafeBrowserViolation(type);
            const heartbeat = () => root.$server.recordSafeBrowserHeartbeat();
            if (!document.fullscreenElement && document.documentElement.requestFullscreen) {
              document.documentElement.requestFullscreen().catch(() => report('FULLSCREEN_EXIT'));
            }
            document.addEventListener('fullscreenchange', () => {
              if (!document.fullscreenElement) report('FULLSCREEN_EXIT');
            });
            document.addEventListener('visibilitychange', () => {
              if (document.hidden) report('TAB_HIDDEN');
            });
            window.addEventListener('blur', () => report('WINDOW_BLUR'));
            window.addEventListener('beforeunload', () => report('BEFORE_UNLOAD'));
            heartbeat();
            root.__safeBrowserHeartbeat = window.setInterval(heartbeat, 10000);
            """);
    }

    @ClientCallable
    public void reportSafeBrowserViolation(String eventType) {
        if (assignmentId == null) {
            return;
        }
        try {
            assignment = safeBrowserModeService.reportViolation(assignmentId, SafeBrowserEventType.valueOf(eventType));
            renderAssignment();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    @ClientCallable
    public void recordSafeBrowserHeartbeat() {
        if (assignmentId == null) {
            return;
        }
        safeBrowserModeService.recordHeartbeat(assignmentId);
    }

    private void showCompletionDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Actividad finalizada");
        dialog.add(new Paragraph(SUBMITTED_MESSAGE));

        var homeButton = new Button("Volver al inicio", _ -> {
            dialog.close();
            UI.getCurrent().getPage().setLocation("/student");
        });
        homeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(homeButton);
        dialog.open();
    }

    private void updateComposerState() {
        var enabled = assignment != null
                && assignment.getStatus() != TrainingActivityAssignmentStatus.SUBMITTED
                && !isBlocked(assignment)
                && (!assignment.getTrainingActivity().isSafeBrowserEnabled() || assignment.isSafeBrowserSessionActive());
        composer.setComposerEnabled(enabled);
        composer.setSubmitEnabled(enabled && !composer.getValue().trim().isBlank());
    }

    private List<CodeMessageListItem> toMessages(TrainingActivityAssignment assignment) {
        var messages = new ArrayList<CodeMessageListItem>();
        var messageTime = assignment.getStartedAt() != null ? assignment.getStartedAt() : assignment.getAssignedAt();
        var offset = 0;

        for (var exchange : evaluationService.readEvaluationTranscript(assignment)) {
            messages.add(assistantMessage(exchange.question(), messageTime.plusMillis(offset++)));
            messages.add(userMessage(exchange.answer(), messageTime.plusMillis(offset++)));
        }

        if (assignment.getCurrentQuestion() != null && !assignment.getCurrentQuestion().isBlank()) {
            messages.add(assistantMessage(assignment.getCurrentQuestion(), messageTime.plusMillis(offset++)));
        }

        if (assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            messages.add(assistantMessage(SUBMITTED_MESSAGE, submittedMessageTime(assignment, messageTime, offset)));
        }
        if (assignment.isSafeBrowserLocked()) {
            messages.add(assistantMessage(LOCKED_MESSAGE, Instant.now()));
        }
        if (assignment.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED
                && assignment.getStatus() != TrainingActivityAssignmentStatus.SUBMITTED) {
            messages.add(assistantMessage("La ventana de evaluación terminó.", Instant.now()));
        }

        return messages;
    }

    private Instant submittedMessageTime(TrainingActivityAssignment assignment, Instant messageTime, int offset) {
        return assignment.getSubmittedAt() != null ? assignment.getSubmittedAt() : messageTime.plusMillis(offset);
    }

    private CodeMessageListItem assistantMessage(String content, Instant createdAt) {
        var item = new CodeMessageListItem(content, createdAt, TUTOR_NAME);
        item.setUserColorIndex();
        item.addClass(UiCss.CONVERSATION_MESSAGE_ASSISTANT);
        return item;
    }

    private CodeMessageListItem userMessage(String content, Instant createdAt) {
        var item = new CodeMessageListItem(content, createdAt, STUDENT_NAME);
        item.setUserColorIndex();
        item.addClass(UiCss.CONVERSATION_MESSAGE_USER);
        return item;
    }
}
