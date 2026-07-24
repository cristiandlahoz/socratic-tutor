package com.wornux.ui.training_activity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.wornux.config.ApplicationProperties;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.entities.training_activity.SafeBrowserEventType;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.chat.ModelAvailabilityStatus;
import com.wornux.services.training_activity.AdaptiveTutorStartUnavailableException;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingAssignmentEvaluationService;
import com.wornux.services.training_activity.TrainingActivityAssignmentSnapshot;
import com.wornux.ui.MainLayout;
import com.wornux.ui.conversation.MessagesList;
import com.wornux.ui.conversation.MessageItem;
import com.wornux.ui.conversation.ConversationComposer;
import com.wornux.ui.conversation.ConversationState;
import com.wornux.ui.css.UiCss;
import com.wornux.ui.student.StudentWorkspaceView;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Route(value = "training-activity/assignments", layout = MainLayout.class)
@PermitAll
@RequiresPermission(AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW)
public class TrainingAssignmentView extends Composite<Div>
        implements HasUrlParameter<String>, BeforeEnterObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrainingAssignmentView.class);

    private static final String ASSIGNMENT_SHELL_CLASS = "assignment-shell-hidden";
    private static final String SAFE_BROWSER_REENTRY_ATTRIBUTE = "data-safe-browser-reentry";
    private static final String TUTOR_NAME = "Tutor Socrático";
    private static final String STUDENT_NAME = "Tú";
    private static final String QUESTION_LOADING_LABEL = "Generando pregunta";
    private static final String ANSWER_PLACEHOLDER = "Escribe tu respuesta aquí...";
    private static final String SUBMITTED_PLACEHOLDER = "Actividad finalizada";
    private static final String SUBMITTED_MESSAGE =
            "La actividad formativa ha culminado, muchas gracias!";
    private static final String LOCKED_MESSAGE =
            "Safe Browser Mode fue interrumpido. Tu profesor debe revisar o desbloquear esta asignación.";

    private final TrainingAssignmentEvaluationService evaluationService;
    private final SafeBrowserModeService safeBrowserModeService;
    private final SafeBrowserAssignmentStateBus assignmentStateBus;
    private final Executor assignmentStartExecutor;
    private final MessagesList messageList = new MessagesList();
    private final ConversationState composerState = new ConversationState();
    private final ConversationComposer composer;
    private final Div reviewAppBar = new Div();
    private final Div safeBrowserEntry = new Div();
    private final Div startRecoveryNotice = new Div();
    private final Div inputShell = new Div();
    private UUID assignmentId;
    private TrainingActivityAssignment assignment;
    private TrainingActivityAssignmentSnapshot assignmentSnapshot;
    private AutoCloseable assignmentStateSubscription;
    private Registration assignmentRefreshPollRegistration;
    private String assignmentStartFailureMessage = "";
    private boolean activityClosedNoticeShown;
    private boolean lastClosedNonSubmittedBlocked;
    private String safeBrowserSessionToken;
    private UUID pendingAnswerSubmissionId;
    private boolean assignmentAccessDenied;
    private boolean assignmentStartInFlight;
    private boolean completionDialogShown;

    @org.springframework.beans.factory.annotation.Autowired
    public TrainingAssignmentView(
            TrainingAssignmentEvaluationService evaluationService,
            SafeBrowserModeService safeBrowserModeService,
            SafeBrowserAssignmentStateBus assignmentStateBus,
            ApplicationProperties.Ai.Conversation chatProperties,
            @Qualifier("assignmentStartExecutor") Executor assignmentStartExecutor) {
        this.evaluationService = evaluationService;
        this.safeBrowserModeService = safeBrowserModeService;
        this.assignmentStateBus = assignmentStateBus;
        this.assignmentStartExecutor = assignmentStartExecutor;

        configureReviewAppBar();

        messageList.setThinkingSpinner(chatProperties.getUi().getThinkingSpinner());
        messageList.setWidthFull();

        composerState.modelAvailabilityStatus().set(ModelAvailabilityStatus.CONNECTED);
        composerState.responseInProgress().set(true);
        composer = new ConversationComposer(composerState, chatProperties.composerPromptLimit(), this::submitAnswer);
        composer.setAllowEmptySubmit(false);

        UiCss.CONVERSATION_COMPOSER.addTo(inputShell);
        inputShell.add(composer);

        var conversationStack = new Div(messageList);
        UiCss.CONVERSATION_THREAD.addTo(conversationStack);

        var historyScroller = new Div(conversationStack);
        historyScroller.setSizeFull();
        UiCss.CONVERSATION_SCROLL_REGION.addTo(historyScroller);

        var chatPane = new Div(safeBrowserEntry, startRecoveryNotice, historyScroller, inputShell);
        chatPane.setSizeFull();
        UiCss.CONVERSATION_PANE.addTo(chatPane);

        var content = getContent();
        content.setSizeFull();
        UiCss.CONVERSATION_VIEW.addTo(content);
        content.add(reviewAppBar, chatPane);
    }

    private void configureReviewAppBar() {
        var title = new Span("Revisión de actividad finalizada");
        UiCss.CONVERSATION_REVIEW_APP_BAR_TITLE.addTo(title);

        var backButton = new Button("Volver al panel de estudiante", _ -> {
            setAssignmentShellHidden(false);
            UI.getCurrent().navigate(StudentWorkspaceView.class);
        });
        backButton.setIcon(VaadinIcon.ARROW_LEFT.create());
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        UiCss.CONVERSATION_REVIEW_APP_BAR_BACK_BUTTON.addTo(backButton);

        UiCss.CONVERSATION_REVIEW_APP_BAR.addTo(reviewAppBar);
        reviewAppBar.add(backButton, title);
        reviewAppBar.setVisible(false);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (assignment == null) {
            setAssignmentShellHidden(true);
        }
        else {
            renderAssignment();
        }
        subscribeToAssignmentStateChanges(attachEvent.getUI());
        startAssignmentRefreshPolling(attachEvent.getUI());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        unsubscribeFromAssignmentStateChanges();
        stopAssignmentRefreshPolling();
        detachSafeBrowserClientHooks();
        setAssignmentShellHidden(false);
        super.onDetach(detachEvent);
    }

    private void subscribeToAssignmentStateChanges(UI ui) {
        unsubscribeFromAssignmentStateChanges();
        assignmentStateSubscription = assignmentStateBus.subscribe(notification -> {
            if (!notification.affectsAssignment(assignmentId)) {
                return;
            }
            ui.access(() -> refreshAssignmentFromPersistence(ui));
        });
    }

    private void startAssignmentRefreshPolling(UI ui) {
        stopAssignmentRefreshPolling();
        assignmentRefreshPollRegistration = ui.addPollListener(_ -> refreshAssignmentFromPersistence(ui));
        ui.setPollInterval(5_000);
    }

    private void stopAssignmentRefreshPolling() {
        if (assignmentRefreshPollRegistration != null) {
            assignmentRefreshPollRegistration.remove();
            assignmentRefreshPollRegistration = null;
        }
    }

    private void refreshAssignmentFromPersistence(UI ui) {
        if (assignmentId == null) {
            return;
        }
        try {
            var wasSubmitted = isSubmittedReview(assignment);
            replaceAssignment(evaluationService.getForCurrentStudent(assignmentId));
            renderAssignment();
            if (!wasSubmitted && isSubmittedReview(assignment)) {
                openCompletionDialog();
            }
        }
        catch (IllegalArgumentException | SecurityException exception) {
            denyAssignmentAccess(ui);
        }
    }

    private void denyAssignmentAccess(UI ui) {
        clearDeniedAssignmentAccess();
        ui.navigate("no-access");
    }

    private void clearDeniedAssignmentAccess() {
        assignmentId = null;
        assignment = null;
        assignmentSnapshot = null;
        unsubscribeFromAssignmentStateChanges();
        stopAssignmentRefreshPolling();
        detachSafeBrowserClientHooks();
        Notification.show("No tienes acceso a esta actividad.");
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
            assignmentAccessDenied = false;
            assignmentId = UUID.fromString(parameter);
            activityClosedNoticeShown = false;
            lastClosedNonSubmittedBlocked = false;
            assignmentStartInFlight = false;
            completionDialogShown = false;
            clearAssignmentStartFailure();
            replaceAssignment(evaluationService.getForCurrentStudent(assignmentId));
            renderAssignment();
        }
        catch (IllegalArgumentException | SecurityException exception) {
            clearDeniedAssignmentAccess();
            assignmentAccessDenied = true;
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (assignmentAccessDenied) {
            assignmentAccessDenied = false;
            event.forwardTo("no-access");
        }
    }

    private void renderAssignment() {
        if (assignment != null && assignmentSnapshot == null) {
            assignmentSnapshot = new TrainingActivityAssignmentSnapshot(assignment, List.of(), null, 0);
        }
        if (assignment != null
                && assignmentSnapshot.currentQuestion() != null
                && !assignmentSnapshot.currentQuestion().isBlank()) {
            clearAssignmentStartFailure();
        }
        var reviewMode = isSubmittedReview(assignment);
        var closedNonSubmittedBlocked = isClosedNonSubmittedBlocked(assignment);
        setAssignmentShellHidden(!reviewMode);
        reviewAppBar.setVisible(reviewMode);
        renderSafeBrowserEntry();
        renderStartRecoveryNotice();
        messageList.setItems(toMessages(assignment));
        maybeShowClosedActivityDialog(closedNonSubmittedBlocked);
        lastClosedNonSubmittedBlocked = closedNonSubmittedBlocked;
        if (reviewMode) {
            inputShell.setVisible(false);
            clearComposer();
            detachSafeBrowserClientHooks();
            updateComposerState();
            return;
        }
        if (isBlocked(assignment)) {
            inputShell.setVisible(true);
            clearComposer();
            detachSafeBrowserClientHooks();
            updateComposerState();
            return;
        }
        inputShell.setVisible(true);
        clearComposer();
        updateComposerState();
        startNonSafeBrowserAssignment();
    }

    private void submitAnswer() {
        var answer = composerState.composerText().peek();
        if (assignmentId == null) {
            return;
        }
        try {
            if (answer == null || answer.trim().isBlank()) {
                Notification.show("Escribe una respuesta antes de continuar");
                return;
            }
            composerState.responseInProgress().set(true);
            if (pendingAnswerSubmissionId == null) {
                pendingAnswerSubmissionId = UUID.randomUUID();
            }
            replaceAssignment(evaluationService.submitAnswer(assignmentId, answer, pendingAnswerSubmissionId));
            pendingAnswerSubmissionId = null;
            clearComposer();
            renderAssignment();
        }
        catch (RuntimeException exception) {
            composerState.responseInProgress().set(false);
            Notification.show(resolveSubmissionFailureMessage(exception));
        }
    }

    private String resolveSubmissionFailureMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "No se pudo procesar tu respuesta.";
        }
        return exception.getMessage();
    }

    private boolean isBlocked(TrainingActivityAssignment assignment) {
        return assignment.isSafeBrowserLocked()
                || assignment.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED;
    }

    private boolean isSubmittedReview(TrainingActivityAssignment assignment) {
        return assignment != null
                && assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED;
    }

    private boolean isClosedNonSubmittedBlocked(TrainingActivityAssignment assignment) {
        return assignment != null
                && assignment.getTrainingActivity().getStatus() == TrainingActivityLifecycleStatus.CLOSED
                && assignment.getStatus() != TrainingActivityAssignmentStatus.SUBMITTED;
    }

    protected void setAssignmentShellHidden(boolean hidden) {
        getUI().ifPresent(ui -> ui.getPage().executeJs(
                "document.querySelector('vaadin-app-layout')?.classList.toggle($0, $1)",
                ASSIGNMENT_SHELL_CLASS, hidden));
    }

    private void renderSafeBrowserEntry() {
        safeBrowserEntry.removeAll();
        safeBrowserEntry.setVisible(assignment != null
                && assignment.getTrainingActivity().isSafeBrowserEnabled()
                && !isSubmittedReview(assignment));
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
            var fullscreenButton = new Button("Entrar a pantalla completa", _ -> enterSafeBrowserFullscreen());
            fullscreenButton.addThemeVariants(ButtonVariant.PRIMARY);
            fullscreenButton.getElement().setAttribute(SAFE_BROWSER_REENTRY_ATTRIBUTE, "true");
            fullscreenButton.getElement().setAttribute("hidden", "");
            safeBrowserEntry.add(
                    new Paragraph(
                            "Safe Browser Mode activo. Mantén esta pestaña visible y la pantalla completa. "
                                    + "Este modo detecta señales del navegador, pero no controla el sistema operativo."),
                    fullscreenButton);
            syncSafeBrowserReentryButton();
            return;
        }
        var instructions = new Paragraph(
                "Esta actividad requiere Safe Browser Mode. Al iniciar, acepta pantalla completa y mantén la pestaña visible. "
                        + "Detectamos salida de pantalla completa, cambios de pestaña y pérdida de foco; este modo no controla el sistema operativo.");
        var startButton = new Button("Iniciar evaluación protegida", _ -> startSafeBrowserSession());
        startButton.addThemeVariants(ButtonVariant.PRIMARY);
        safeBrowserEntry.add(instructions, startButton);
    }

    private void syncSafeBrowserReentryButton() {
        safeBrowserEntry.getElement().executeJs("""
            const entry = this;
            const sync = () => {
              const button = entry.querySelector('[' + $0 + ']');
              if (button) button.hidden = Boolean(document.fullscreenElement);
            };
            if (!entry.__safeBrowserReentrySync) {
              entry.__safeBrowserReentrySync = sync;
              entry.__safeBrowserReentryCleanup = () => {
                document.removeEventListener('fullscreenchange', entry.__safeBrowserReentrySync);
                entry.__safeBrowserReentrySync = undefined;
                entry.__safeBrowserReentryCleanup = undefined;
              };
              document.addEventListener('fullscreenchange', sync);
            }
            sync();
            """, SAFE_BROWSER_REENTRY_ATTRIBUTE);
    }

    private void startSafeBrowserSession() {
        if (isSafeBrowserLockedLocally()) {
            renderAssignment();
            return;
        }
        getElement().executeJs("""
            const root = this;
            const enterFullscreen = () => {
              if (document.fullscreenElement || !document.documentElement.requestFullscreen) {
                return Promise.resolve(true);
              }
              return document.documentElement.requestFullscreen().then(() => true).catch(() => false);
            };
            enterFullscreen().then((granted) => root.$server.startSafeBrowserSessionAfterFullscreen(granted));
            """);
    }

    private void enterSafeBrowserFullscreen() {
        if (isSafeBrowserLockedLocally()) {
            renderAssignment();
            return;
        }
        getElement().executeJs("""
            const root = this;
            const enterFullscreen = () => {
              if (document.fullscreenElement || !document.documentElement.requestFullscreen) {
                return Promise.resolve(true);
              }
              return document.documentElement.requestFullscreen().then(() => true).catch(() => false);
            };
            enterFullscreen().then((granted) => {
              if (granted) {
                root.__safeBrowserArmed = true;
                root.__safeBrowserSuppressUntil = Date.now() + 1200;
              }
              root.$server.safeBrowserFullscreenResult(granted);
            });
            """);
        if (safeBrowserSessionToken != null) {
            installSafeBrowserRuntime(safeBrowserSessionToken);
        }
    }

    @ClientCallable
    public void safeBrowserFullscreenResult(boolean fullscreenGranted) {
        if (!fullscreenGranted) {
            Notification.show("Acepta pantalla completa para continuar con Safe Browser Mode.");
        }
    }

    @ClientCallable
    public void startSafeBrowserSessionAfterFullscreen(boolean fullscreenGranted) {
        var safeBrowserSessionStarted = false;
        try {
            if (isSafeBrowserLockedLocally()) {
                renderAssignment();
                return;
            }
            if (!fullscreenGranted) {
                Notification.show("Acepta pantalla completa para iniciar Safe Browser Mode.");
                return;
            }
            var session = safeBrowserModeService.beginSession(assignmentId);
            safeBrowserSessionToken = session.token();
            assignment = safeBrowserModeService.recordHeartbeat(assignmentId, safeBrowserSessionToken);
            assignmentSnapshot = evaluationService.snapshot(assignment);
            safeBrowserSessionStarted = true;
            replaceAssignment(evaluationService.start(assignmentId));
            clearAssignmentStartFailure();
            renderAssignment();
            installSafeBrowserRuntime(safeBrowserSessionToken);
        }
        catch (AdaptiveTutorStartUnavailableException exception) {
            if (safeBrowserSessionStarted) {
                deactivateSafeBrowserSessionAfterStartFailure(exception);
            }
            showRecoverableStartFailure(exception);
            renderAssignment();
            Notification.show(exception.getMessage());
        }
        catch (RuntimeException exception) {
            if (safeBrowserSessionStarted) {
                deactivateSafeBrowserSessionAfterStartFailure(exception);
            }
            Notification.show(exception.getMessage());
        }
    }

    private void deactivateSafeBrowserSessionAfterStartFailure(RuntimeException originalException) {
        try {
            if (safeBrowserSessionToken != null) {
                assignment = safeBrowserModeService.deactivateSession(assignmentId, safeBrowserSessionToken);
                assignmentSnapshot = evaluationService.snapshot(assignment);
            }
        }
        catch (RuntimeException cleanupException) {
            LOGGER.warn(
                    "Failed to deactivate Safe Browser session after assignment start failure: assignmentId={}",
                    assignmentId,
                    cleanupException);
            if (originalException != cleanupException) {
                originalException.addSuppressed(cleanupException);
            }
        }
    }

    private void renderStartRecoveryNotice() {
        startRecoveryNotice.removeAll();
        var temporaryTutorFailure = assignment != null
                && assignment.getStatus() == TrainingActivityAssignmentStatus.TEMPORARILY_UNAVAILABLE;
        var visible = temporaryTutorFailure
                || (assignmentStartFailureMessage != null && !assignmentStartFailureMessage.isBlank());
        startRecoveryNotice.setVisible(visible);
        if (!visible) {
            return;
        }
        if (temporaryTutorFailure) {
            var retryButton = new Button("Reintentar tutor", _ -> retryTutor());
            retryButton.addThemeVariants(ButtonVariant.PRIMARY);
            startRecoveryNotice.add(new Paragraph("El tutor no está disponible temporalmente. Tu respuesta fue guardada."), retryButton);
            return;
        }
        startRecoveryNotice.add(new Paragraph(assignmentStartFailureMessage));
        if (!canRetryAssignmentStart()) {
            return;
        }
        var retryButton = new Button(retryStartButtonLabel(), _ -> retryAssignmentStart());
        retryButton.addThemeVariants(ButtonVariant.PRIMARY);
        startRecoveryNotice.add(retryButton);
    }

    private boolean canRetryAssignmentStart() {
        return assignmentId != null
                && assignment != null
                && assignment.getStatus() == TrainingActivityAssignmentStatus.ASSIGNED
                && assignment.getTrainingActivity().getStatus() != TrainingActivityLifecycleStatus.CLOSED
                && !assignment.isSafeBrowserLocked();
    }

    private String retryStartButtonLabel() {
        return assignment != null && assignment.getTrainingActivity().isSafeBrowserEnabled()
                ? "Reintentar Safe Browser Mode"
                : "Reintentar tutor";
    }

    private void retryAssignmentStart() {
        if (!canRetryAssignmentStart()) {
            return;
        }
        try {
            clearAssignmentStartFailure();
            if (assignment.getTrainingActivity().isSafeBrowserEnabled()) {
                startSafeBrowserSession();
                return;
            }
            startNonSafeBrowserAssignment();
        }
        catch (AdaptiveTutorStartUnavailableException exception) {
            showRecoverableStartFailure(exception);
            renderAssignment();
            Notification.show(exception.getMessage());
        }
    }

    private void startNonSafeBrowserAssignment() {
        if (assignmentStartInFlight
                || assignmentId == null
                || assignment == null
                || assignment.getTrainingActivity().isSafeBrowserEnabled()
                || assignment.getStatus() != TrainingActivityAssignmentStatus.ASSIGNED
                || !assignmentStartFailureMessage.isBlank()) {
            return;
        }
        var targetAssignmentId = assignmentId;
        var studentMemberId = assignment.getGroupClassMember().getId();
        var ui = getUI().orElse(null);
        if (ui == null) {
            return;
        }
        assignmentStartInFlight = true;
        try {
            assignmentStartExecutor.execute(() -> startNonSafeBrowserAssignment(targetAssignmentId, studentMemberId, ui));
        }
        catch (RuntimeException exception) {
            assignmentStartInFlight = false;
            showRecoverableStartFailure(exception);
            renderAssignment();
        }
    }

    private void startNonSafeBrowserAssignment(UUID targetAssignmentId, UUID studentMemberId, UI ui) {
        try {
            var started = evaluationService.startForStudent(targetAssignmentId, studentMemberId);
            ui.access(() -> applyNonSafeBrowserStart(targetAssignmentId, started, null));
        }
        catch (RuntimeException exception) {
            ui.access(() -> applyNonSafeBrowserStart(targetAssignmentId, null, exception));
        }
    }

    private void applyNonSafeBrowserStart(
            UUID targetAssignmentId,
            TrainingActivityAssignmentSnapshot started,
            RuntimeException failure) {
        if (!isAttached() || !targetAssignmentId.equals(assignmentId)) {
            assignmentStartInFlight = false;
            return;
        }
        assignmentStartInFlight = false;
        if (failure != null) {
            showRecoverableStartFailure(failure);
            renderAssignment();
            return;
        }
        replaceAssignment(started);
        clearAssignmentStartFailure();
        renderAssignment();
    }

    private void retryTutor() {
        if (assignmentId == null) {
            return;
        }
        try {
            replaceAssignment(evaluationService.retryTutor(assignmentId));
            renderAssignment();
        }
        catch (RuntimeException exception) {
            Notification.show(resolveSubmissionFailureMessage(exception));
        }
    }

    private void showRecoverableStartFailure(RuntimeException exception) {
        assignmentStartFailureMessage = resolveSubmissionFailureMessage(exception);
        LOGGER.warn(
                "Training assignment start remains recoverable and retryable: assignmentId={} reason={}",
                assignmentId,
                exception.getMessage(),
                exception);
    }

    private void clearAssignmentStartFailure() {
        assignmentStartFailureMessage = "";
    }

    private void installSafeBrowserRuntime(String sessionToken) {
        getElement().executeJs("""
            const root = this;
            const sessionToken = $0;
            if (root.__safeBrowserInstalled) return;
            root.__safeBrowserInstalled = true;
            const STARTUP_GRACE_MS = 4000;
            const FOCUS_GRACE_MS = 1200;
            root.__safeBrowserArmed = Boolean(document.fullscreenElement);
            root.__safeBrowserSuppressUntil = Date.now() + STARTUP_GRACE_MS;
            const suppressed = (type) => {
              if (Date.now() < root.__safeBrowserSuppressUntil) return true;
              // Opening the browser fullscreen permission UI can blur the window before
              // the student is actually in the monitored session. Do not treat that as
              // a student escape attempt; only report focus/fullscreen exits after the
              // runtime has observed a successful fullscreen entry.
              if (!root.__safeBrowserArmed && (type === 'WINDOW_BLUR' || type === 'FULLSCREEN_EXIT' || type === 'BEFORE_UNLOAD')) {
                return true;
              }
              return false;
            };
            const eventId = () => crypto.randomUUID();
            const report = (type) => {
              if (!suppressed(type)) root.$server.reportSafeBrowserViolation(type, sessionToken, eventId());
            };
            const heartbeat = () => root.$server.recordSafeBrowserHeartbeat(sessionToken);
            if (!document.fullscreenElement && document.documentElement.requestFullscreen) {
              document.documentElement.requestFullscreen()
                .then(() => {
                  root.__safeBrowserArmed = true;
                  root.__safeBrowserSuppressUntil = Date.now() + FOCUS_GRACE_MS;
                })
                .catch(() => {
                  root.__safeBrowserArmed = false;
                  root.__safeBrowserSuppressUntil = Date.now() + STARTUP_GRACE_MS;
                });
            }
            const onFullscreenChange = () => {
              if (document.fullscreenElement) {
                root.__safeBrowserArmed = true;
                root.__safeBrowserSuppressUntil = Date.now() + FOCUS_GRACE_MS;
                return;
              }
              if (root.__safeBrowserArmed) report('FULLSCREEN_EXIT');
            };
            const onVisibilityChange = () => {
              if (document.hidden) report('TAB_HIDDEN');
            };
            const onBlur = () => report('WINDOW_BLUR');
            const onBeforeUnload = () => report('BEFORE_UNLOAD');
            document.addEventListener('fullscreenchange', onFullscreenChange);
            document.addEventListener('visibilitychange', onVisibilityChange);
            window.addEventListener('blur', onBlur);
            window.addEventListener('beforeunload', onBeforeUnload);
            heartbeat();
            root.__safeBrowserHeartbeat = window.setInterval(heartbeat, 10000);
            root.__safeBrowserCleanup = () => {
              document.removeEventListener('fullscreenchange', onFullscreenChange);
              document.removeEventListener('visibilitychange', onVisibilityChange);
              window.removeEventListener('blur', onBlur);
              window.removeEventListener('beforeunload', onBeforeUnload);
              if (root.__safeBrowserHeartbeat) {
                window.clearInterval(root.__safeBrowserHeartbeat);
                root.__safeBrowserHeartbeat = undefined;
              }
              root.__safeBrowserInstalled = false;
              root.__safeBrowserCleanup = undefined;
            };
            """, sessionToken);
    }

    private void detachSafeBrowserClientHooks() {
        getElement().executeJs("""
            const root = this;
            if (root.__safeBrowserCleanup) {
              root.__safeBrowserCleanup();
            }
            const entry = root.querySelector('.safe-browser-entry');
            if (entry && entry.__safeBrowserReentryCleanup) {
              entry.__safeBrowserReentryCleanup();
            }
            """);
    }

    @ClientCallable
    public void reportSafeBrowserViolation(String eventType, String sessionToken, String clientEventId) {
        if (assignmentId == null) {
            return;
        }
        try {
            assignment = safeBrowserModeService.reportViolation(
                    assignmentId, sessionToken, SafeBrowserEventType.valueOf(eventType), UUID.fromString(clientEventId));
            assignmentSnapshot = evaluationService.snapshot(assignment);
            renderAssignment();
        }
        catch (RuntimeException exception) {
            Notification.show(exception.getMessage());
        }
    }

    @ClientCallable
    public void recordSafeBrowserHeartbeat(String sessionToken) {
        if (assignmentId == null) {
            return;
        }
        assignment = safeBrowserModeService.recordHeartbeat(assignmentId, sessionToken);
        assignmentSnapshot = evaluationService.snapshot(assignment);
    }

    private void showCompletionDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Actividad finalizada");
        dialog.add(new Paragraph(SUBMITTED_MESSAGE));

        var continueButton = new Button("Seguir viendo", _ -> dialog.close());
        var homeButton = new Button("Volver al panel estudiantil", _ -> {
            dialog.close();
            setAssignmentShellHidden(false);
            UI.getCurrent().navigate(StudentWorkspaceView.class);
        });
        homeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(continueButton, homeButton);
        dialog.open();
        onDialogOpened(dialog);
    }

    public void openCompletionDialog() {
        if (completionDialogShown || !isSubmittedReview(assignment)) {
            return;
        }
        completionDialogShown = true;
        showCompletionDialog();
    }

    private void maybeShowClosedActivityDialog(boolean closedNonSubmittedBlocked) {
        if (activityClosedNoticeShown || !closedNonSubmittedBlocked || lastClosedNonSubmittedBlocked) {
            return;
        }
        activityClosedNoticeShown = true;
        var dialog = new Dialog();
        dialog.setHeaderTitle("Actividad finalizada");
        dialog.add(new Paragraph("La actividad formativa terminó. Ya puedes volver al panel estudiantil."));

        var backButton = new Button("Volver al panel estudiantil", _ -> {
            dialog.close();
            setAssignmentShellHidden(false);
            UI.getCurrent().navigate(StudentWorkspaceView.class);
        });
        backButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(backButton);
        dialog.open();
        onDialogOpened(dialog);
    }

    protected void onDialogOpened(Dialog dialog) {
    }

    private void updateComposerState() {
        var enabled = isComposerReady();
        composerState.responseInProgress().set(!enabled);
        composerState.modelAvailabilityStatus().set(enabled ? ModelAvailabilityStatus.CONNECTED : ModelAvailabilityStatus.OFFLINE);
    }

    private boolean isComposerReady() {
        return assignment != null
                && assignment.getStatus() != TrainingActivityAssignmentStatus.SUBMITTED
                && !isBlocked(assignment)
                && assignmentSnapshot.currentQuestion() != null
                && !assignmentSnapshot.currentQuestion().isBlank()
                && (!assignment.getTrainingActivity().isSafeBrowserEnabled() || assignment.isSafeBrowserSessionActive());
    }

    private boolean isSafeBrowserLockedLocally() {
        return assignment != null && assignment.isSafeBrowserLocked();
    }

    private void focusComposerIfReady() {
        if (!isComposerReady()) {
            return;
        }
        requestComposerFocus();
    }

    protected void requestComposerFocus() {
        composer.getElement().executeJs(
                "requestAnimationFrame(() => this.querySelector('vaadin-text-area')?.focus())");
    }

    private void clearComposer() {
        composerState.composerText().set("");
    }

    private List<MessageItem> toMessages(TrainingActivityAssignment assignment) {
        var messages = new ArrayList<MessageItem>();
        var messageTime = assignment.getStartedAt() != null ? assignment.getStartedAt() : assignment.getAssignedAt();
        var offset = 0;

        for (var exchange : assignmentSnapshot.transcript()) {
            messages.add(assistantMessage(exchange.question(), messageTime.plusMillis(offset++)));
            messages.add(userMessage(exchange.answer(), messageTime.plusMillis(offset++)));
        }

        if (assignmentSnapshot.currentQuestion() != null && !assignmentSnapshot.currentQuestion().isBlank()) {
            messages.add(assistantMessage(assignmentSnapshot.currentQuestion(), messageTime.plusMillis(offset++)));
        }

        if (assignment.getStatus() == TrainingActivityAssignmentStatus.SUBMITTED) {
            messages.add(assistantMessage(SUBMITTED_MESSAGE, submittedMessageTime(assignment, messageTime, offset)));
        }
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.STARTING) {
            messages.add(assistantLoadingMessage(Instant.now(), "Preparando primera pregunta"));
        }
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.WAITING_FOR_TUTOR) {
            messages.add(assistantLoadingMessage(Instant.now(), "Analizando respuesta"));
        }
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.TEMPORARILY_UNAVAILABLE) {
            messages.add(assistantMessage("El tutor no está disponible temporalmente. Tu respuesta fue guardada.", Instant.now()));
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

    private MessageItem assistantMessage(String content, Instant createdAt) {
        return new MessageItem(content, createdAt, TUTOR_NAME, MessageItem.Variant.ASSISTANT, false, true);
    }

    private MessageItem assistantLoadingMessage(Instant createdAt) {
        return assistantLoadingMessage(createdAt, QUESTION_LOADING_LABEL);
    }

    private MessageItem assistantLoadingMessage(Instant createdAt, String label) {
        return new MessageItem("", createdAt, TUTOR_NAME, MessageItem.Variant.ASSISTANT, true, false,
                label);
    }

    private MessageItem userMessage(String content, Instant createdAt) {
        return new MessageItem(content, createdAt, STUDENT_NAME, MessageItem.Variant.USER, false, false);
    }

    private void replaceAssignment(TrainingActivityAssignmentSnapshot snapshot) {
        assignmentSnapshot = snapshot;
        assignment = snapshot.assignment();
    }
}
