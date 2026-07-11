package com.wornux.ui.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEvent;
import com.wornux.config.ApplicationProperties;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.services.chat.ModelAvailabilityStatus;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.AdaptiveTutorStartUnavailableException;
import com.wornux.services.training_activity.TrainingAssignmentEvaluationService;
import com.wornux.ui.conversation.ConversationState;
import com.wornux.ui.conversation.MessageItem;
import com.wornux.ui.conversation.MessagesList;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class TrainingAssignmentViewTest {

    @Test
    void submitAnswerRendersCanonicalMessagesAfterSynchronousCompletion() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var initialAssignment = assignment(TrainingActivityAssignmentStatus.STARTED, "¿Qué entiendes inicialmente?");
            var savedAssignment = assignment(TrainingActivityAssignmentStatus.STARTED, "¿Qué ejemplo lo demuestra?");
            ReflectionTestUtils.setField(savedAssignment, "id", field(initialAssignment, "id"));
            ReflectionTestUtils.setField(savedAssignment, "trainingActivity", field(initialAssignment, "trainingActivity"));
            ReflectionTestUtils.setField(savedAssignment, "groupClassMember", field(initialAssignment, "groupClassMember"));
            ReflectionTestUtils.setField(savedAssignment, "assignedAt", field(initialAssignment, "assignedAt"));
            ReflectionTestUtils.setField(savedAssignment, "startedAt", field(initialAssignment, "startedAt"));
            ReflectionTestUtils.setField(savedAssignment, "updatedAt", Instant.now());
            ReflectionTestUtils.setField(savedAssignment, "questionCount", 2);

            when(evaluationService.readEvaluationTranscript(initialAssignment)).thenReturn(List.of());
            when(evaluationService.readEvaluationTranscript(savedAssignment)).thenReturn(List.of(
                    new TrainingAssignmentEvaluationService.EvaluationExchange(
                            "¿Qué entiendes inicialmente?",
                            "Mi respuesta")));
            var sink = Sinks.many().unicast().<TrainingAssignmentEvaluationService.AnswerStreamEvent>onBackpressureBuffer();
            when(evaluationService.answerStream(field(initialAssignment, "id"), "Mi respuesta")).thenReturn(sink.asFlux());

            var view = new TrainingAssignmentView(
                    evaluationService,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));
            ReflectionTestUtils.setField(view, "assignmentId", field(initialAssignment, "id"));
            ReflectionTestUtils.setField(view, "assignment", initialAssignment);
            ReflectionTestUtils.invokeMethod(view, "renderAssignment");

            var composerState = composerState(view);
            composerState.composerText().set("Mi respuesta");

            ReflectionTestUtils.invokeMethod(view, "submitAnswer");

            var messageList = messageList(view);
            assertThat(messageList.getElement().getProperty("thinkingSpinner")).isEqualTo("dna");
            assertThat(messageList.getItems()).hasSize(3);
            assertThat(messageLoading(messageList.getItems().getLast())).isTrue();
            assertThat(messageLoadingLabel(messageList.getItems().getLast())).isEqualTo("Generando pregunta");
            assertThat(composerState.composerText().peek()).isEmpty();
            assertThat(composerState.responseInProgress().peek()).isTrue();
            assertThat(messageList.getItems())
                    .extracting(TrainingAssignmentViewTest::messageText)
                    .containsExactly(
                            "¿Qué entiendes inicialmente?",
                            "Mi respuesta",
                            "");

            sink.tryEmitNext(TrainingAssignmentEvaluationService.AnswerStreamEvent.messageDelta("¿Qué ejemplo "));
            sink.tryEmitNext(TrainingAssignmentEvaluationService.AnswerStreamEvent.completed(savedAssignment));
            sink.tryEmitComplete();

            assertThat(composerState.responseInProgress().peek()).isFalse();
            assertThat(messageList.getItems())
                    .extracting(TrainingAssignmentViewTest::messageText)
                    .containsExactly(
                            "¿Qué entiendes inicialmente?",
                            "Mi respuesta",
                            "¿Qué ejemplo lo demuestra?");
            assertThat(messageList.getItems())
                    .filteredOn(item -> "assistant".equals(item.getVariant()))
                    .allSatisfy(item -> assertThat(messageDebuggable(item)).isTrue());
            assertThat(messageList.getItems())
                    .filteredOn(item -> "user".equals(item.getVariant()))
                    .allSatisfy(item -> assertThat(messageDebuggable(item)).isFalse());
            assertThat(composerState.modelAvailabilityStatus().peek()).isEqualTo(ModelAvailabilityStatus.CONNECTED);
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void submitAnswerRestoresComposerAndCanonicalMessagesWhenEvaluationFails() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var initialAssignment = assignment(TrainingActivityAssignmentStatus.STARTED, "¿Qué entiendes inicialmente?");

            when(evaluationService.readEvaluationTranscript(initialAssignment)).thenReturn(List.of());
            var sink = Sinks.many().unicast().<TrainingAssignmentEvaluationService.AnswerStreamEvent>onBackpressureBuffer();
            when(evaluationService.answerStream(field(initialAssignment, "id"), "Mi respuesta"))
                    .thenReturn(sink.asFlux());

            var view = new TrainingAssignmentView(
                    evaluationService,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));
            ReflectionTestUtils.setField(view, "assignmentId", field(initialAssignment, "id"));
            ReflectionTestUtils.setField(view, "assignment", initialAssignment);
            ReflectionTestUtils.invokeMethod(view, "renderAssignment");

            var composerState = composerState(view);
            composerState.composerText().set("Mi respuesta");

            ReflectionTestUtils.invokeMethod(view, "submitAnswer");

            var messageList = messageList(view);
            assertThat(messageList.getItems())
                    .extracting(TrainingAssignmentViewTest::messageText)
                    .containsExactly("¿Qué entiendes inicialmente?", "Mi respuesta", "");

            sink.tryEmitError(new IllegalStateException("No se pudo procesar tu respuesta."));

            assertThat(messageList.getItems()).extracting(TrainingAssignmentViewTest::messageText).containsExactly("¿Qué entiendes inicialmente?");
            assertThat(composerState.composerText().peek()).isEqualTo("Mi respuesta");
            assertThat(composerState.responseInProgress().peek()).isFalse();
            assertThat(composerState.modelAvailabilityStatus().peek()).isEqualTo(ModelAvailabilityStatus.CONNECTED);
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void submitAnswerRestoresComposerWhenAnswerStreamCreationFailsSynchronously() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var initialAssignment = assignment(TrainingActivityAssignmentStatus.STARTED, "¿Qué entiendes inicialmente?");

            when(evaluationService.readEvaluationTranscript(initialAssignment)).thenReturn(List.of());
            when(evaluationService.answerStream(field(initialAssignment, "id"), "Mi respuesta"))
                    .thenThrow(new IllegalStateException("No se pudo procesar tu respuesta."));

            var view = new TrainingAssignmentView(
                    evaluationService,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));
            ReflectionTestUtils.setField(view, "assignmentId", field(initialAssignment, "id"));
            ReflectionTestUtils.setField(view, "assignment", initialAssignment);
            ReflectionTestUtils.invokeMethod(view, "renderAssignment");

            var composerState = composerState(view);
            composerState.composerText().set("Mi respuesta");

            ReflectionTestUtils.invokeMethod(view, "submitAnswer");

            var messageList = messageList(view);
            assertThat(messageList.getItems()).extracting(TrainingAssignmentViewTest::messageText)
                    .containsExactly("¿Qué entiendes inicialmente?");
            assertThat(composerState.composerText().peek()).isEqualTo("Mi respuesta");
            assertThat(composerState.responseInProgress().peek()).isFalse();
            assertThat(composerState.modelAvailabilityStatus().peek()).isEqualTo(ModelAvailabilityStatus.CONNECTED);
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void submitAnswerKeepsCanonicalStateWhenStreamFailsAfterPersistence() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var initialAssignment = assignment(TrainingActivityAssignmentStatus.STARTED, "¿Qué entiendes inicialmente?");
            var persistedAssignment = assignment(TrainingActivityAssignmentStatus.STARTED, "¿Qué ejemplo lo demuestra?");
            ReflectionTestUtils.setField(persistedAssignment, "id", field(initialAssignment, "id"));
            ReflectionTestUtils.setField(persistedAssignment, "trainingActivity", field(initialAssignment, "trainingActivity"));
            ReflectionTestUtils.setField(persistedAssignment, "groupClassMember", field(initialAssignment, "groupClassMember"));
            ReflectionTestUtils.setField(persistedAssignment, "assignedAt", field(initialAssignment, "assignedAt"));
            ReflectionTestUtils.setField(persistedAssignment, "startedAt", field(initialAssignment, "startedAt"));
            ReflectionTestUtils.setField(persistedAssignment, "updatedAt", Instant.now());
            ReflectionTestUtils.setField(persistedAssignment, "questionCount", 2);

            when(evaluationService.readEvaluationTranscript(initialAssignment)).thenReturn(List.of());
            when(evaluationService.readEvaluationTranscript(persistedAssignment)).thenReturn(List.of(
                    new TrainingAssignmentEvaluationService.EvaluationExchange(
                            "¿Qué entiendes inicialmente?",
                            "Mi respuesta")));
            when(evaluationService.answerStream(field(initialAssignment, "id"), "Mi respuesta"))
                    .thenReturn(Flux.error(new IllegalStateException("publish failed")));
            when(evaluationService.getForCurrentStudent(eq(field(initialAssignment, "id")))).thenReturn(persistedAssignment);

            var view = new TrainingAssignmentView(
                    evaluationService,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));
            ReflectionTestUtils.setField(view, "assignmentId", field(initialAssignment, "id"));
            ReflectionTestUtils.setField(view, "assignment", initialAssignment);
            ReflectionTestUtils.invokeMethod(view, "renderAssignment");

            var composerState = composerState(view);
            composerState.composerText().set("Mi respuesta");

            ReflectionTestUtils.invokeMethod(view, "submitAnswer");

            assertThat(messageList(view).getItems())
                    .extracting(TrainingAssignmentViewTest::messageText)
                    .containsExactly(
                            "¿Qué entiendes inicialmente?",
                            "Mi respuesta",
                            "¿Qué ejemplo lo demuestra?");
            assertThat(composerState.composerText().peek()).isEmpty();
            assertThat(composerState.responseInProgress().peek()).isFalse();
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void lockedSafeBrowserStateWinsOverActiveSessionUiAndStartAttempts() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var safeBrowserModeService = mock(SafeBrowserModeService.class);
            var lockedAssignment = assignment(TrainingActivityAssignmentStatus.STARTED, null);
            var activity = (TrainingActivity) field(lockedAssignment, "trainingActivity");
            ReflectionTestUtils.setField(activity, "safeBrowserEnabled", true);
            ReflectionTestUtils.setField(lockedAssignment, "safeBrowserLocked", true);
            ReflectionTestUtils.setField(lockedAssignment, "safeBrowserSessionActive", true);
            when(evaluationService.readEvaluationTranscript(lockedAssignment)).thenReturn(List.of());

            var view = new TrainingAssignmentView(
                    evaluationService,
                    safeBrowserModeService,
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));
            ReflectionTestUtils.setField(view, "assignmentId", field(lockedAssignment, "id"));
            ReflectionTestUtils.setField(view, "assignment", lockedAssignment);

            ReflectionTestUtils.invokeMethod(view, "renderAssignment");
            ReflectionTestUtils.invokeMethod(view, "startSafeBrowserSessionAfterFullscreen", true);

            assertThat(componentText(safeBrowserEntry(view))).contains("Safe Browser Mode fue interrumpido");
            assertThat(componentText(safeBrowserEntry(view))).doesNotContain("Safe Browser Mode activo");
            assertThat(composerState(view).modelAvailabilityStatus().peek()).isEqualTo(ModelAvailabilityStatus.OFFLINE);
            verify(safeBrowserModeService, never()).beginSession(any());
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void safeBrowserStartFailureDeactivatesSessionWithoutLockingAssignment() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var safeBrowserModeService = mock(SafeBrowserModeService.class);
            var assignment = assignment(TrainingActivityAssignmentStatus.ASSIGNED, null);
            var activity = (TrainingActivity) field(assignment, "trainingActivity");
            ReflectionTestUtils.setField(activity, "safeBrowserEnabled", true);
            when(evaluationService.readEvaluationTranscript(assignment)).thenReturn(List.of());
            when(safeBrowserModeService.beginSession(field(assignment, "id")))
                    .thenReturn(new SafeBrowserModeService.SessionStart(UUID.randomUUID(), "safe-browser-token"));
            when(safeBrowserModeService.recordHeartbeat(field(assignment, "id"), "safe-browser-token")).thenAnswer(_ -> {
                ReflectionTestUtils.setField(assignment, "safeBrowserSessionActive", true);
                return assignment;
            });
            when(evaluationService.start(field(assignment, "id")))
                    .thenThrow(new AdaptiveTutorStartUnavailableException(
                            new IllegalStateException("The adaptive tutor must start with a question.")));
            when(safeBrowserModeService.deactivateSession(field(assignment, "id"), "safe-browser-token")).thenAnswer(_ -> {
                ReflectionTestUtils.setField(assignment, "safeBrowserSessionActive", false);
                return assignment;
            });

            var view = new TrainingAssignmentView(
                    evaluationService,
                    safeBrowserModeService,
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));
            ReflectionTestUtils.setField(view, "assignmentId", field(assignment, "id"));
            ReflectionTestUtils.setField(view, "assignment", assignment);

            ReflectionTestUtils.invokeMethod(view, "startSafeBrowserSessionAfterFullscreen", true);

            assertThat((Boolean) field(assignment, "safeBrowserSessionActive")).isFalse();
            assertThat((Boolean) field(assignment, "safeBrowserLocked")).isFalse();
            assertThat(componentText(startRecoveryNotice(view))).contains("No fue posible continuar la tutoría");
            assertThat(findButtonByText(startRecoveryNotice(view), "Reintentar Safe Browser Mode")).isNotNull();
            verify(safeBrowserModeService).beginSession(field(assignment, "id"));
            verify(safeBrowserModeService).recordHeartbeat(field(assignment, "id"), "safe-browser-token");
            verify(evaluationService).start(field(assignment, "id"));
            verify(safeBrowserModeService).deactivateSession(field(assignment, "id"), "safe-browser-token");
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void submittedAssignmentUsesReviewNavigationWhileActivityIsStillPublished() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var submittedAssignment = assignment(TrainingActivityAssignmentStatus.SUBMITTED, null);
            when(evaluationService.readEvaluationTranscript(submittedAssignment)).thenReturn(List.of());

            var view = new TrainingAssignmentView(
                    evaluationService,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));
            ReflectionTestUtils.setField(view, "assignmentId", field(submittedAssignment, "id"));
            ReflectionTestUtils.setField(view, "assignment", submittedAssignment);

            ReflectionTestUtils.invokeMethod(view, "renderAssignment");

            assertThat(reviewAppBar(view).isVisible()).isTrue();
            assertThat(componentText(reviewAppBar(view))).contains("Volver al panel de estudiante");
            assertThat(inputShell(view).isVisible()).isFalse();
            assertThat(safeBrowserEntry(view).isVisible()).isFalse();
            assertThat(composerState(view).composerText().peek()).isEmpty();
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void submittedAssignmentUsesReviewNavigationWhileActivityIsClosed() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var submittedAssignment = assignment(TrainingActivityAssignmentStatus.SUBMITTED, null);
            var activity = (TrainingActivity) field(submittedAssignment, "trainingActivity");
            ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.CLOSED);
            when(evaluationService.readEvaluationTranscript(submittedAssignment)).thenReturn(List.of());

            var view = new CapturingTrainingAssignmentView(
                    evaluationService,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));
            ReflectionTestUtils.setField(view, "assignmentId", field(submittedAssignment, "id"));
            ReflectionTestUtils.setField(view, "assignment", submittedAssignment);

            ReflectionTestUtils.invokeMethod(view, "renderAssignment");

            assertThat(reviewAppBar(view).isVisible()).isTrue();
            assertThat(componentText(reviewAppBar(view))).contains("Volver al panel de estudiante");
            assertThat(inputShell(view).isVisible()).isFalse();
            assertThat(safeBrowserEntry(view).isVisible()).isFalse();
            assertThat(view.lastDialog).isNull();
            assertThat(composerState(view).composerText().peek()).isEmpty();
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void submittedSafeBrowserAssignmentHidesSafeBrowserEntryAndUsesReviewNavigation() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var submittedAssignment = assignment(TrainingActivityAssignmentStatus.SUBMITTED, null);
            var activity = (TrainingActivity) field(submittedAssignment, "trainingActivity");
            ReflectionTestUtils.setField(activity, "safeBrowserEnabled", true);
            ReflectionTestUtils.setField(submittedAssignment, "safeBrowserSessionActive", true);
            when(evaluationService.readEvaluationTranscript(submittedAssignment)).thenReturn(List.of());

            var view = new TrainingAssignmentView(
                    evaluationService,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));
            ReflectionTestUtils.setField(view, "assignmentId", field(submittedAssignment, "id"));
            ReflectionTestUtils.setField(view, "assignment", submittedAssignment);

            ReflectionTestUtils.invokeMethod(view, "renderAssignment");

            assertThat(reviewAppBar(view).isVisible()).isTrue();
            assertThat(inputShell(view).isVisible()).isFalse();
            assertThat(safeBrowserEntry(view).isVisible()).isFalse();
            assertThat(componentText(safeBrowserEntry(view))).doesNotContain("Start Safe Browser Mode");
            assertThat(composerState(view).modelAvailabilityStatus().peek()).isEqualTo(ModelAvailabilityStatus.OFFLINE);
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void submitAnswerRequestsComposerFocusAfterSuccessfulCompletion() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var initialAssignment = assignment(TrainingActivityAssignmentStatus.STARTED, "¿Qué entiendes inicialmente?");
            var savedAssignment = assignment(TrainingActivityAssignmentStatus.STARTED, "¿Qué ejemplo lo demuestra?");
            ReflectionTestUtils.setField(savedAssignment, "id", field(initialAssignment, "id"));
            ReflectionTestUtils.setField(savedAssignment, "trainingActivity", field(initialAssignment, "trainingActivity"));
            ReflectionTestUtils.setField(savedAssignment, "groupClassMember", field(initialAssignment, "groupClassMember"));
            ReflectionTestUtils.setField(savedAssignment, "questionCount", 2);
            when(evaluationService.readEvaluationTranscript(initialAssignment)).thenReturn(List.of());
            when(evaluationService.readEvaluationTranscript(savedAssignment)).thenReturn(List.of(
                    new TrainingAssignmentEvaluationService.EvaluationExchange("¿Qué entiendes inicialmente?", "Mi respuesta")));
            when(evaluationService.answerStream(field(initialAssignment, "id"), "Mi respuesta")).thenReturn(Flux.just(
                    TrainingAssignmentEvaluationService.AnswerStreamEvent.completed(savedAssignment)));

            var view = new CapturingTrainingAssignmentView(
                    evaluationService,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));
            ReflectionTestUtils.setField(view, "assignmentId", field(initialAssignment, "id"));
            ReflectionTestUtils.setField(view, "assignment", initialAssignment);
            ReflectionTestUtils.invokeMethod(view, "renderAssignment");
            composerState(view).composerText().set("Mi respuesta");

            ReflectionTestUtils.invokeMethod(view, "submitAnswer");

            awaitUntil(() -> view.focusRequests > 0);
            assertThat(view.focusRequests).isEqualTo(1);
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void onDetachDisposesActiveAnswerStream() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var cancelled = new AtomicBoolean(false);
            var initialAssignment = assignment(TrainingActivityAssignmentStatus.STARTED, "¿Qué entiendes inicialmente?");
            when(evaluationService.readEvaluationTranscript(initialAssignment)).thenReturn(List.of());
            when(evaluationService.answerStream(field(initialAssignment, "id"), "Mi respuesta"))
                    .thenReturn(Flux.<TrainingAssignmentEvaluationService.AnswerStreamEvent>never().doOnCancel(() -> cancelled.set(true)));

            var view = new CapturingTrainingAssignmentView(
                    evaluationService,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));
            ReflectionTestUtils.setField(view, "assignmentId", field(initialAssignment, "id"));
            ReflectionTestUtils.setField(view, "assignment", initialAssignment);
            ReflectionTestUtils.invokeMethod(view, "renderAssignment");
            composerState(view).composerText().set("Mi respuesta");

            ReflectionTestUtils.invokeMethod(view, "submitAnswer");
            view.detachForTest();

            awaitUntil(cancelled::get);
            assertThat(cancelled.get()).isTrue();
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void setParameterDoesNotAutoStartClosedExpiredAssignments() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var closedAssignment = assignment(TrainingActivityAssignmentStatus.EXPIRED, null);
            var closedActivity = (TrainingActivity) field(closedAssignment, "trainingActivity");
            ReflectionTestUtils.setField(closedActivity, "status", TrainingActivityLifecycleStatus.CLOSED);
            when(evaluationService.readEvaluationTranscript(closedAssignment)).thenReturn(List.of());
            when(evaluationService.getForCurrentStudent(field(closedAssignment, "id"))).thenReturn(closedAssignment);

            var view = new CapturingTrainingAssignmentView(
                    evaluationService,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));

            view.setParameter(mock(BeforeEvent.class), field(closedAssignment, "id").toString());

            verify(evaluationService, never()).start(field(closedAssignment, "id"));
            assertThat(messageList(view).getItems())
                    .extracting(TrainingAssignmentViewTest::messageText)
                    .contains("La ventana de evaluación terminó.");
            assertThat(composerState(view).modelAvailabilityStatus().peek()).isEqualTo(ModelAvailabilityStatus.OFFLINE);
            assertThat(view.lastDialog).isNotNull();
            assertThat(view.lastDialog.isOpened()).isTrue();
            assertThat(componentText(view.lastDialog)).contains("La actividad formativa terminó. Ya puedes volver al panel estudiantil.");
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void setParameterShowsRecoverableRetryStateWhenTutorCannotStart() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var pendingAssignment = assignment(TrainingActivityAssignmentStatus.ASSIGNED, null);
            when(evaluationService.readEvaluationTranscript(pendingAssignment)).thenReturn(List.of());
            when(evaluationService.getForCurrentStudent(field(pendingAssignment, "id"))).thenReturn(pendingAssignment);
            when(evaluationService.start(field(pendingAssignment, "id")))
                    .thenThrow(new AdaptiveTutorStartUnavailableException(new IllegalStateException("boom")));

            var view = new CapturingTrainingAssignmentView(
                    evaluationService,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));

            view.setParameter(mock(BeforeEvent.class), field(pendingAssignment, "id").toString());

            assertThat(composerState(view).modelAvailabilityStatus().peek()).isEqualTo(ModelAvailabilityStatus.OFFLINE);
            assertThat(componentText(startRecoveryNotice(view))).contains("No fue posible continuar la tutoría");
            assertThat(findButtonByText(startRecoveryNotice(view), "Reintentar tutor")).isNotNull();
            verify(evaluationService).start(field(pendingAssignment, "id"));
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void showsClosedActivityDialogOnlyAfterAssignmentTransitionsToClosedBlockedState() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var evaluationService = mock(TrainingAssignmentEvaluationService.class);
            var initialAssignment = assignment(TrainingActivityAssignmentStatus.STARTED, "¿Qué entiendes inicialmente?");
            var closedAssignment = assignment(TrainingActivityAssignmentStatus.EXPIRED, null);
            ReflectionTestUtils.setField(closedAssignment, "id", field(initialAssignment, "id"));
            ReflectionTestUtils.setField(closedAssignment, "trainingActivity", field(initialAssignment, "trainingActivity"));
            ReflectionTestUtils.setField(closedAssignment, "groupClassMember", field(initialAssignment, "groupClassMember"));
            ReflectionTestUtils.setField(closedAssignment, "assignedAt", field(initialAssignment, "assignedAt"));
            ReflectionTestUtils.setField(closedAssignment, "startedAt", field(initialAssignment, "startedAt"));
            ReflectionTestUtils.setField(closedAssignment, "updatedAt", Instant.now());
            var closedActivity = (TrainingActivity) field(closedAssignment, "trainingActivity");

            when(evaluationService.readEvaluationTranscript(initialAssignment)).thenReturn(List.of());
            when(evaluationService.readEvaluationTranscript(closedAssignment)).thenReturn(List.of());
            when(evaluationService.getForCurrentStudent(field(initialAssignment, "id"))).thenReturn(closedAssignment);

            var view = new CapturingTrainingAssignmentView(
                    evaluationService,
                    mock(SafeBrowserModeService.class),
                    new SafeBrowserAssignmentStateBus(),
                    chatProperties("dna"));
            ReflectionTestUtils.setField(view, "assignmentId", field(initialAssignment, "id"));
            ReflectionTestUtils.setField(view, "assignment", initialAssignment);

            assertThat(view.lastDialog).isNull();

            ReflectionTestUtils.invokeMethod(view, "renderAssignment");
            assertThat(view.lastDialog).isNull();

            ReflectionTestUtils.setField(closedActivity, "status", TrainingActivityLifecycleStatus.CLOSED);
            ReflectionTestUtils.setField(view, "assignment", closedAssignment);
            ReflectionTestUtils.invokeMethod(view, "renderAssignment");

            assertThat(view.lastDialog).isNotNull();
            assertThat(view.lastDialog.isOpened()).isTrue();
            assertThat(componentText(view.lastDialog)).contains("La actividad formativa terminó. Ya puedes volver al panel estudiantil.");

            var firstDialog = view.lastDialog;
            ReflectionTestUtils.invokeMethod(view, "renderAssignment");

            assertThat(view.lastDialog).isSameAs(firstDialog);
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void parseQuestionsSupportsLegacyAndCompactReportFormats() {
        var dialog = dialog();

        var questions = parseQuestions(dialog, """
                ### Pregunta 1
                ¿Qué entiendes inicialmente?
                **Respuesta del estudiante:**
                Entiendo lo básico.

                Pregunta: ¿Qué ejemplo lo demuestra?
                Respuesta del estudiante: Un puntero que recorre un arreglo.

                Pregunta 7: ¿Qué pasaría si el índice falla?
                **Respuesta del estudiante:** Se rompe el acceso al arreglo.
                """);

        assertThat(questions).hasSize(3);
        assertThat((Integer) field(questions.get(0), "number")).isEqualTo(1);
        assertThat((String) field(questions.get(0), "tutorPrompt")).isEqualTo("¿Qué entiendes inicialmente?");
        assertThat((String) field(questions.get(0), "studentAnswer")).isEqualTo("Entiendo lo básico.");
        assertThat((Integer) field(questions.get(1), "number")).isEqualTo(2);
        assertThat((String) field(questions.get(1), "tutorPrompt")).isEqualTo("¿Qué ejemplo lo demuestra?");
        assertThat((String) field(questions.get(1), "studentAnswer")).isEqualTo("Un puntero que recorre un arreglo.");
        assertThat((Integer) field(questions.get(2), "number")).isEqualTo(7);
    }

    @Test
    void parseQuestionsPreservesFencedCodeBlocksAndSkipsIncompleteCards() {
        var dialog = dialog();

        var questions = parseQuestions(dialog, """
                Pregunta: Explica este ejemplo
                **Respuesta del estudiante:**
                ```c
                int main(void) {
                  return 0;
                }
                ```

                Pregunta 2
                Falta respuesta
                """);

        assertThat(questions).hasSize(1);
        assertThat((String) field(questions.getFirst(), "studentAnswer")).isEqualTo("""
                ```c
                int main(void) {
                  return 0;
                }
                ```""");
    }

    @Test
    void fallbackReportDisablesDebugControlsForTrainingAssignmentReports() {
        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(activity, "title", "Actividad final");
        ReflectionTestUtils.setField(activity, "instructions", "Describe tu razonamiento.");
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.PUBLISHED);

        var assignment = assignment(TrainingActivityAssignmentStatus.SUBMITTED, null);
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "submittedAt", Instant.now());

        var dialog = new TrainingActivityDialog(
                activity,
                mock(com.wornux.services.training_activity.TrainingActivityService.class),
                mock(SafeBrowserModeService.class),
                new SafeBrowserAssignmentStateBus(),
                _ -> {},
                () -> {});

        var content = (Div) ReflectionTestUtils.invokeMethod(
                dialog,
                "fallbackReport",
                "```c\nint main(void) { return 0; }\n```",
                assignment);

        var reportList = (MessagesList) content.getChildren().findFirst().orElseThrow();
        assertThat(reportList.getItems()).singleElement().satisfies(item -> {
            assertThat(messageDebuggable(item)).isFalse();
            assertThat(messageLoading(item)).isFalse();
        });
    }

    @Test
    void tutorQuestionsKeepCodeBlocksEnabled() {
        var assignment = assignment(TrainingActivityAssignmentStatus.STARTED, "Observa esta variante:\n\n```c\nfor (int i = 0; i < 3; i++)\n    printf(\"%d\", i);\n```\n\n¿Cuántas veces se ejecuta printf y por qué?");
        var evaluationService = mock(TrainingAssignmentEvaluationService.class);
        when(evaluationService.readEvaluationTranscript(assignment)).thenReturn(List.of());

        var view = new TrainingAssignmentView(
                evaluationService,
                mock(SafeBrowserModeService.class),
                new SafeBrowserAssignmentStateBus(),
                chatProperties("dna"));
        ReflectionTestUtils.setField(view, "assignment", assignment);

        ReflectionTestUtils.invokeMethod(view, "renderAssignment");

        assertThat(messageList(view).getItems())
                .filteredOn(item -> "assistant".equals(item.getVariant()))
                .allSatisfy(item -> assertThat(messageDebuggable(item)).isTrue());
    }

    @Test
    void reportBodyRendersReportCardsWithSerializedItemsJson() {
        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(activity, "title", "Actividad final");
        ReflectionTestUtils.setField(activity, "instructions", "Describe tu razonamiento.");
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.PUBLISHED);

        var assignment = assignment(TrainingActivityAssignmentStatus.SUBMITTED, null);
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "finalReport", """
                ### Pregunta 1
                ¿Qué entiendes inicialmente?
                **Respuesta del estudiante:**
                Entiendo lo básico.
                """);

        var dialog = new TrainingActivityDialog(
                activity,
                mock(com.wornux.services.training_activity.TrainingActivityService.class),
                mock(SafeBrowserModeService.class),
                new SafeBrowserAssignmentStateBus(),
                _ -> {},
                () -> {});

        var content = (Component) ReflectionTestUtils.invokeMethod(dialog, "reportBody", assignment);
        var reportCards = findDescendant(content, TrainingActivityReportCards.class);

        assertThat(reportCards).isNotNull();
        assertThat(reportCards.getElement().getProperty("itemsJson"))
                .contains("¿Qué entiendes inicialmente?")
                .contains("Entiendo lo básico.");
    }

    @Test
    void activityDialogRefreshesSnapshotBeforeRenderingOnBusNotification() {
        var activityId = UUID.randomUUID();
        var initialActivity = new TrainingActivity();
        ReflectionTestUtils.setField(initialActivity, "id", activityId);
        ReflectionTestUtils.setField(initialActivity, "title", "Actividad final");
        ReflectionTestUtils.setField(initialActivity, "instructions", "Describe tu razonamiento.");
        ReflectionTestUtils.setField(initialActivity, "status", TrainingActivityLifecycleStatus.PUBLISHED);

        var closedActivity = new TrainingActivity();
        ReflectionTestUtils.setField(closedActivity, "id", activityId);
        ReflectionTestUtils.setField(closedActivity, "title", "Actividad final");
        ReflectionTestUtils.setField(closedActivity, "instructions", "Describe tu razonamiento.");
        ReflectionTestUtils.setField(closedActivity, "status", TrainingActivityLifecycleStatus.CLOSED);

        var trainingActivityService = mock(com.wornux.services.training_activity.TrainingActivityService.class);
        var safeBrowserModeService = mock(SafeBrowserModeService.class);
        when(trainingActivityService.listAssignments(activityId)).thenReturn(List.of());
        when(trainingActivityService.get(activityId)).thenReturn(closedActivity);
        when(safeBrowserModeService.listOpenAlerts(activityId)).thenReturn(List.of());

        var dialog = new TrainingActivityDialog(
                initialActivity,
                trainingActivityService,
                safeBrowserModeService,
                new SafeBrowserAssignmentStateBus(),
                _ -> {},
                () -> {});

        assertThat(findButtonByText(dialog, "Cerrar actividad").isEnabled()).isTrue();

        ReflectionTestUtils.invokeMethod(dialog, "refreshActivitySnapshot");
        ReflectionTestUtils.invokeMethod(dialog, "renderActivityMode");

        assertThat(findButtonByText(dialog, "Cerrar actividad").isEnabled()).isFalse();
    }

    private static ApplicationProperties.Ai.Conversation chatProperties(String thinkingSpinner) {
        var properties = new ApplicationProperties.Ai.Conversation();
        properties.setContextWindowTokens(2_000);
        properties.getUi().setThinkingSpinner(thinkingSpinner);
        return properties;
    }

    private static ConversationState composerState(TrainingAssignmentView view) {
        return (ConversationState) ReflectionTestUtils.getField(view, "composerState");
    }

    private static MessagesList messageList(TrainingAssignmentView view) {
        return (MessagesList) ReflectionTestUtils.getField(view, "messageList");
    }

    private static Div safeBrowserEntry(TrainingAssignmentView view) {
        return (Div) ReflectionTestUtils.getField(view, "safeBrowserEntry");
    }

    private static Div reviewAppBar(TrainingAssignmentView view) {
        return (Div) ReflectionTestUtils.getField(view, "reviewAppBar");
    }

    private static Div inputShell(TrainingAssignmentView view) {
        return (Div) ReflectionTestUtils.getField(view, "inputShell");
    }

    private static Div startRecoveryNotice(TrainingAssignmentView view) {
        return (Div) ReflectionTestUtils.getField(view, "startRecoveryNotice");
    }

    private static TrainingActivityAssignment assignment(
            TrainingActivityAssignmentStatus status,
            String currentQuestion) {
        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(activity, "title", "Actividad formativa");
        ReflectionTestUtils.setField(activity, "instructions", "Responde con tus propias palabras.");
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.PUBLISHED);

        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());

        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "groupClassMember", member);
        ReflectionTestUtils.setField(assignment, "status", status);
        ReflectionTestUtils.setField(assignment, "assignedAt", Instant.now());
        ReflectionTestUtils.setField(assignment, "startedAt", Instant.now());
        ReflectionTestUtils.setField(assignment, "updatedAt", Instant.now());
        ReflectionTestUtils.setField(assignment, "currentQuestion", currentQuestion);
        ReflectionTestUtils.setField(assignment, "evaluationTranscript", "[]");
        ReflectionTestUtils.setField(assignment, "questionCount", currentQuestion == null ? 0 : 1);
        return assignment;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) {
        return (T) ReflectionTestUtils.getField(target, name);
    }

    private static boolean messageDebuggable(MessageItem item) {
        return field(item, "debuggableCodeBlocks");
    }

    private static boolean messageLoading(MessageItem item) {
        return field(item, "loading");
    }

    private static String messageText(MessageItem item) {
        return field(item, "text");
    }

    private static String messageLoadingLabel(MessageItem item) {
        return field(item, "loadingLabel");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> parseQuestions(TrainingActivityDialog dialog, String report) {
        return (List<Object>) ReflectionTestUtils.invokeMethod(dialog, "parseQuestions", report);
    }

    private static TrainingActivityDialog dialog() {
        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(activity, "title", "Actividad final");
        ReflectionTestUtils.setField(activity, "instructions", "Describe tu razonamiento.");
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.PUBLISHED);
        return new TrainingActivityDialog(
                activity,
                mock(com.wornux.services.training_activity.TrainingActivityService.class),
                mock(SafeBrowserModeService.class),
                new SafeBrowserAssignmentStateBus(),
                _ -> {},
                () -> {});
    }

    private static String componentText(Component component) {
        var ownText = component.getElement().getText();
        return Stream.concat(Stream.of(ownText == null ? "" : ownText), component.getChildren().map(TrainingAssignmentViewTest::componentText))
                .filter(text -> !text.isBlank())
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
    }

    private static <T extends Component> T findDescendant(Component root, Class<T> type) {
        return descendants(root)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElse(null);
    }

    private static Button findButtonByText(Component root, String text) {
        return descendants(root)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(TrainingAssignmentViewTest::descendants));
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition) {
        var deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for async UI update.", exception);
            }
        }
        throw new AssertionError("Timed out waiting for async UI update.");
    }

    private static final class CapturingTrainingAssignmentView extends TrainingAssignmentView {

        private Dialog lastDialog;
        private int focusRequests;

        private CapturingTrainingAssignmentView(
                TrainingAssignmentEvaluationService evaluationService,
                SafeBrowserModeService safeBrowserModeService,
                SafeBrowserAssignmentStateBus assignmentStateBus,
                ApplicationProperties.Ai.Conversation chatProperties) {
            super(evaluationService, safeBrowserModeService, assignmentStateBus, chatProperties);
        }

        @Override
        protected void onDialogOpened(Dialog dialog) {
            this.lastDialog = dialog;
        }

        @Override
        protected void requestComposerFocus() {
            focusRequests++;
        }

        private void detachForTest() {
            super.onDetach(mock(DetachEvent.class));
        }
    }
}
