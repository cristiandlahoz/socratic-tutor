package com.wornux.ui.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.router.Route;
import com.wornux.config.ApplicationProperties;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingAssignmentEvaluationService;
import com.wornux.services.training_activity.TrainingActivityAssignmentSnapshot;
import com.wornux.ui.conversation.ConversationState;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrainingAssignmentViewTest extends BrowserlessTest {

    @Test
    void af4_blankComposerInputIsRejectedInTheUiBeforeTheDurableCommand() {
        var ui = UI.getCurrent();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var properties = new ApplicationProperties.Ai.Conversation();
            properties.setContextWindowTokens(2000);
            var view = new TrainingAssignmentView(evaluationService, mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(), properties, Runnable::run);
            ReflectionTestUtils.setField(view, "assignmentId", UUID.randomUUID());
            var state = (ConversationState) ReflectionTestUtils.getField(view, "composerState");
            state.composerText().set(" \t ");
            UI.setCurrent(ui);

            ReflectionTestUtils.invokeMethod(view, "submitAnswer");

            verify(evaluationService, never()).submitAnswer(any(), any(), any());
        }
        finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void af1_deniedPersistedRefreshNavigatesToNoAccessWithoutRethrowingTheServiceException() {
        var ui = new TrackingUi();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var assignmentId = UUID.randomUUID();
            var properties = new ApplicationProperties.Ai.Conversation();
            properties.setContextWindowTokens(2000);
            var view = new TrainingAssignmentView(evaluationService, mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(), properties, Runnable::run);
            ReflectionTestUtils.setField(view, "assignmentId", assignmentId);
            org.mockito.Mockito.when(evaluationService.getForCurrentStudent(assignmentId))
                    .thenThrow(new SecurityException("Only students can answer assigned evaluations."));

            ReflectionTestUtils.invokeMethod(view, "refreshAssignmentFromPersistence", ui);

            assertThat(ui.navigatedTo).isEqualTo("no-access");
            assertThat(ReflectionTestUtils.getField(view, "assignmentId")).isNull();
        }
        finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void af1_deniedInitialRouteForwardsBeforeTheAssignmentViewIsActivated() {
        var ui = UI.getCurrent();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var assignmentId = UUID.randomUUID();
            var properties = new ApplicationProperties.Ai.Conversation();
            properties.setContextWindowTokens(2000);
            var view = new TrainingAssignmentView(evaluationService, mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(), properties, Runnable::run);
            org.mockito.Mockito.when(evaluationService.getForCurrentStudent(assignmentId))
                    .thenThrow(new SecurityException("Only students can answer assigned evaluations."));
            var beforeEvent = mock(com.vaadin.flow.router.BeforeEvent.class);
            var beforeEnterEvent = mock(com.vaadin.flow.router.BeforeEnterEvent.class);

            view.setParameter(beforeEvent, assignmentId.toString());
            view.beforeEnter(beforeEnterEvent);

            verify(beforeEnterEvent).forwardTo("no-access");
            assertThat(ReflectionTestUtils.getField(view, "assignmentId")).isNull();
        }
        finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void nonSafeBrowserAssignmentStartsOnceFromTheQueuedBackgroundTask() {
        var ui = UI.getCurrent();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var assignment = assignedNonSafeBrowserAssignment();
            var scheduledStarts = new AtomicInteger();
            Executor queuedExecutor = task -> scheduledStarts.incrementAndGet();
            var view = view(evaluationService, queuedExecutor);
            ui.add(view);
            ReflectionTestUtils.setField(view, "assignmentId", assignment.getId());
            ReflectionTestUtils.setField(view, "assignment", assignment);

            ReflectionTestUtils.invokeMethod(view, "renderAssignment");
            ReflectionTestUtils.invokeMethod(view, "renderAssignment");

            assertThat(scheduledStarts.get()).isEqualTo(1);
            verify(evaluationService, never()).start(any());
            verify(evaluationService, never()).startForStudent(any(), any());
        }
        finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void nonSafeBrowserStartFailureRemainsRecoverableWithoutSchedulingAnotherStart() {
        var ui = UI.getCurrent();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var assignment = assignedNonSafeBrowserAssignment();
            var scheduledStarts = new AtomicInteger();
            Executor queuedExecutor = task -> scheduledStarts.incrementAndGet();
            var view = view(evaluationService, queuedExecutor);
            ui.add(view);
            ReflectionTestUtils.setField(view, "assignmentId", assignment.getId());
            ReflectionTestUtils.setField(view, "assignment", assignment);
            ReflectionTestUtils.setField(view, "assignmentStartInFlight", true);

            ReflectionTestUtils.invokeMethod(
                    view, "applyNonSafeBrowserStart", assignment.getId(), null, new IllegalStateException("Tutor no disponible"));

            assertThat(ReflectionTestUtils.getField(view, "assignmentStartFailureMessage"))
                    .isEqualTo("Tutor no disponible");
            assertThat(scheduledStarts.get()).isZero();
        }
        finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void closedActivityDialogClearsTheAssignmentShellBeforeNavigatingToStudentWorkspace() {
        var events = new ArrayList<String>();
        var ui = new OrderedTrackingUi(events);
        UI.setCurrent(ui);
        try {
            var view = new ClosedActivityDialogView(
                    mock(TrainingAssignmentEvaluationService.class), mock(SafeBrowserModeService.class));
            ui.add(view);
            events.clear();
            ui.page.executeJsCalls.clear();

            ReflectionTestUtils.invokeMethod(view, "maybeShowClosedActivityDialog", true);

            $(Button.class).from(view.openedDialog).first().click();

            assertThat(ui.page.executeJsCalls).containsExactly(new JsCall(
                    "document.querySelector('vaadin-app-layout')?.classList.toggle($0, $1)",
                    List.of("assignment-shell-hidden", false)));
            assertThat(events).containsExactly("js:assignment-shell-hidden:false", "navigate:StudentWorkspaceView");
        }
        finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void submittedPublicationImmediatelyRefreshesAndOpensTheExistingCompletionHandoffInsteadOfNavigatingAway() {
        var ui = new TrackingUi();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var assignmentStateBus = new SafeBrowserAssignmentStateBus();
            var assignment = assignedNonSafeBrowserAssignment();
            ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.WAITING_FOR_TUTOR);
            var completed = assignedNonSafeBrowserAssignment();
            ReflectionTestUtils.setField(completed, "id", assignment.getId());
            ReflectionTestUtils.setField(completed, "status", TrainingActivityAssignmentStatus.SUBMITTED);
            var completedSnapshot = new TrainingActivityAssignmentSnapshot(completed, List.of(), null, 1);
            when(evaluationService.getForCurrentStudent(assignment.getId())).thenReturn(completedSnapshot);
            var view = new CompletionDialogView(evaluationService, assignmentStateBus);
            ReflectionTestUtils.setField(view, "assignmentId", assignment.getId());
            ReflectionTestUtils.setField(view, "assignment", assignment);
            ui.add(view);

            assignmentStateBus.publish(new SafeBrowserAssignmentStateBus.Notification(
                    null, assignment.getId(), null, false, false));

            assertThat(ui.accessCalls).isEqualTo(1);
            verify(evaluationService).getForCurrentStudent(assignment.getId());
            assertThat(view.openedDialog).isNotNull();
            assertThat(ui.navigatedTo).isNull();
        }
        finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void repeatedSubmittedPublicationDoesNotOpenADuplicateCompletionDialog() {
        var ui = new TrackingUi();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var assignmentStateBus = new SafeBrowserAssignmentStateBus();
            var assignment = assignedNonSafeBrowserAssignment();
            ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.WAITING_FOR_TUTOR);
            var completed = assignedNonSafeBrowserAssignment();
            ReflectionTestUtils.setField(completed, "id", assignment.getId());
            ReflectionTestUtils.setField(completed, "status", TrainingActivityAssignmentStatus.SUBMITTED);
            var completedSnapshot = new TrainingActivityAssignmentSnapshot(completed, List.of(), null, 1);
            when(evaluationService.getForCurrentStudent(assignment.getId())).thenReturn(completedSnapshot);
            var view = new CompletionDialogView(evaluationService, assignmentStateBus);
            ReflectionTestUtils.setField(view, "assignmentId", assignment.getId());
            ReflectionTestUtils.setField(view, "assignment", assignment);
            ui.add(view);

            var notification = new SafeBrowserAssignmentStateBus.Notification(
                    null, assignment.getId(), null, false, false);
            assignmentStateBus.publish(notification);
            var firstDialog = view.openedDialog;
            assignmentStateBus.publish(notification);

            assertThat(ui.accessCalls).isEqualTo(2);
            assertThat(firstDialog).isNotNull();
            assertThat(view.openedDialog).isSameAs(firstDialog);
        }
        finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void alreadySubmittedAssignmentOpensInReviewModeWithoutCompletionDialog() {
        var ui = UI.getCurrent();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var assignment = assignedNonSafeBrowserAssignment();
            ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.SUBMITTED);
            var snapshot = new TrainingActivityAssignmentSnapshot(assignment, List.of(), null, 1);
            when(evaluationService.getForCurrentStudent(assignment.getId())).thenReturn(snapshot);
            var view = new CompletionDialogView(evaluationService);

            view.setParameter(mock(com.vaadin.flow.router.BeforeEvent.class), assignment.getId().toString());

            assertThat(view.openedDialog).isNull();
            assertThat(((Component) ReflectionTestUtils.getField(view, "reviewAppBar")).isVisible()).isTrue();
        }
        finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void completionDialogOffersContinueAndStudentWorkspaceNavigation() {
        var events = new ArrayList<String>();
        var ui = new OrderedTrackingUi(events);
        UI.setCurrent(ui);
        try {
            var view = new CompletionDialogView(mock(TrainingAssignmentEvaluationService.class));
            var assignment = assignedNonSafeBrowserAssignment();
            ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.SUBMITTED);
            ReflectionTestUtils.setField(view, "assignment", assignment);
            ui.add(view);
            events.clear();

            view.openCompletionDialog();

            var buttons = $(Button.class).from(view.openedDialog).all();
            assertThat(view.openedDialog.getChildren()
                    .filter(Paragraph.class::isInstance)
                    .map(Paragraph.class::cast)
                    .map(Paragraph::getText))
                    .contains("La actividad formativa ha culminado, muchas gracias!");
            assertThat(buttons).extracting(Button::getText)
                    .containsExactly("Seguir viendo", "Volver al panel estudiantil");
            buttons.getLast().click();
            assertThat(events).containsExactly("js:assignment-shell-hidden:false", "navigate:StudentWorkspaceView");
        }
        finally {
            UI.setCurrent(null);
        }
    }

    private static TrainingAssignmentView view(TrainingAssignmentEvaluationService evaluationService, Executor executor) {
        var properties = new ApplicationProperties.Ai.Conversation();
        properties.setContextWindowTokens(2000);
        return new TrainingAssignmentView(
                evaluationService, mock(SafeBrowserModeService.class), new SafeBrowserAssignmentStateBus(), properties, executor);
    }

    private static TrainingActivityAssignment assignedNonSafeBrowserAssignment() {
        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.PUBLISHED);
        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(assignment, "groupClassMember", member);
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.ASSIGNED);
        ReflectionTestUtils.setField(assignment, "assignedAt", java.time.Instant.now());
        return assignment;
    }

    private static final class TrackingUi extends UI {
        private String navigatedTo;
        private int accessCalls;

        private TrackingUi() {
            getInternals().setSession(UI.getCurrent().getSession());
        }

        @Override
        public Future<Void> access(com.vaadin.flow.server.Command command) {
            accessCalls++;
            command.execute();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void navigate(String location) {
            navigatedTo = location;
        }
    }

    private static final class OrderedTrackingUi extends UI {
        private final List<String> events;
        private final RecordingPage page;

        private OrderedTrackingUi(List<String> events) {
            this.events = events;
            page = new RecordingPage(this, events);
            getInternals().setSession(UI.getCurrent().getSession());
        }

        @Override
        public Page getPage() {
            return page;
        }

        @Override
        public <T extends Component> Optional<T> navigate(Class<T> navigationTarget) {
            events.add("navigate:" + navigationTarget.getSimpleName());
            return Optional.empty();
        }
    }

    private static final class RecordingPage extends Page {
        private final List<String> events;
        private final List<JsCall> executeJsCalls = new ArrayList<>();

        private RecordingPage(UI ui, List<String> events) {
            super(ui);
            this.events = events;
        }

        @Override
        public PendingJavaScriptResult executeJs(String expression, Object... parameters) {
            executeJsCalls.add(new JsCall(expression, List.of(parameters)));
            events.add("js:%s:%s".formatted(parameters[0], parameters[1]));
            return null;
        }
    }

    private record JsCall(String expression, List<Object> parameters) {
    }

    @Route("test-closed-training-activity-dialog")
    private static final class ClosedActivityDialogView extends TrainingAssignmentView {
        private Dialog openedDialog;

        private ClosedActivityDialogView(
                TrainingAssignmentEvaluationService evaluationService,
                SafeBrowserModeService safeBrowserModeService) {
            super(evaluationService, safeBrowserModeService, new SafeBrowserAssignmentStateBus(), conversationProperties(), Runnable::run);
        }

        @Override
        protected void onDialogOpened(Dialog dialog) {
            openedDialog = dialog;
        }
    }

    @Route("test-training-completion-dialog")
    private static final class CompletionDialogView extends TrainingAssignmentView {
        private Dialog openedDialog;

        private CompletionDialogView(TrainingAssignmentEvaluationService evaluationService) {
            this(evaluationService, new SafeBrowserAssignmentStateBus());
        }

        private CompletionDialogView(
                TrainingAssignmentEvaluationService evaluationService,
                SafeBrowserAssignmentStateBus assignmentStateBus) {
            super(evaluationService, mock(SafeBrowserModeService.class), assignmentStateBus,
                    conversationProperties(), Runnable::run);
        }

        @Override
        protected void onDialogOpened(Dialog dialog) {
            openedDialog = dialog;
        }
    }

    private static ApplicationProperties.Ai.Conversation conversationProperties() {
        var properties = new ApplicationProperties.Ai.Conversation();
        properties.setContextWindowTokens(2000);
        return properties;
    }
}
