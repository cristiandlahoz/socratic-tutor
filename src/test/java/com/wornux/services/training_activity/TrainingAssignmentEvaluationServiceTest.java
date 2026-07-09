package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.data.entities.training_activity.AnswerQuality;
import com.wornux.data.entities.training_activity.CoverageStatus;
import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.PedagogicalMove;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.json.JsonMapper;

class TrainingAssignmentEvaluationServiceTest {

    @Test
    void startInitializesAssignedEvaluation() {
        var fixture = fixture();

        var assignment = fixture.service.start(fixture.assignmentId);

        assertThat(
            ReflectionTestUtils.getField(assignment, "status")
        ).isEqualTo(TrainingActivityAssignmentStatus.STARTED);
        assertThat(
            ReflectionTestUtils.getField(assignment, "questionCount")
        ).isEqualTo(1);
        assertThat(
            (String) ReflectionTestUtils.getField(assignment, "currentQuestion")
        ).isNotBlank();
        assertThat(
            ReflectionTestUtils.getField(assignment, "startedAt")
        ).isNotNull();
    }

    @Test
    void startLeavesAssignmentRecoverableWhenFirstTutorDecisionFails() {
        var fixture = fixture();
        when(fixture.tutorService.firstDecision(fixture.assignment))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> fixture.service.start(fixture.assignmentId))
                .isInstanceOf(AdaptiveTutorStartUnavailableException.class)
                .hasMessage(AdaptiveTutorStartUnavailableException.PUBLIC_MESSAGE);
        assertThat(ReflectionTestUtils.getField(fixture.assignment, "status"))
                .isEqualTo(TrainingActivityAssignmentStatus.ASSIGNED);
        assertThat((String) ReflectionTestUtils.getField(fixture.assignment, "currentQuestion"))
                .isNull();
        assertThat(ReflectionTestUtils.getField(fixture.assignment, "questionCount"))
                .isEqualTo(0);
    }

    @Test
    void answerPersistsTranscriptAndAdvancesQuestion() {
        var fixture = fixture();
        fixture.service.start(fixture.assignmentId);

        var assignment = fixture.service.answer(
            fixture.assignmentId,
            "I understand the basics."
        );

        assertThat(
            (String) ReflectionTestUtils.getField(
                assignment,
                "evaluationTranscript"
            )
        ).contains("I understand the basics.");
        assertThat(
            ReflectionTestUtils.getField(assignment, "questionCount")
        ).isEqualTo(2);
        assertThat(
            (String) ReflectionTestUtils.getField(assignment, "currentQuestion")
        ).isNotBlank();
        verify(fixture.tutorService, never()).finalReport(any(), anyList(), any());
    }

    @Test
    void answerStreamPersistsTranscriptAndCompletesWithSavedAssignment() {
        var fixture = fixture();
        fixture.service.start(fixture.assignmentId);
        when(fixture.tutorService.nextDecisionStream(eq(fixture.assignment), any(), anyList())).thenReturn(Flux.just(
                TrainingAssignmentTutorService.AdaptiveTutorStreamEvent.textDelta("¿Qué parte "),
                TrainingAssignmentTutorService.AdaptiveTutorStreamEvent.completed(question("¿Qué parte necesita más claridad?"))));

        var events = fixture.service.answerStream(
                fixture.assignmentId,
                "I understand the basics."
        ).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(TrainingAssignmentEvaluationService.AnswerStreamEvent::messageDelta)
                .contains("¿Qué parte ");
        assertThat(events.getLast().assignment()).isNotNull();
        assertThat(ReflectionTestUtils.getField(events.getLast().assignment(), "status")).isEqualTo(
                TrainingActivityAssignmentStatus.STARTED);
        assertThat((String) ReflectionTestUtils.getField(events.getLast().assignment(), "currentQuestion"))
                .isEqualTo("¿Qué parte necesita más claridad?");
    }

    @Test
    void answerStreamRunsTerminalCompletionWorkOnBoundedElastic() {
        var fixture = fixture();
        fixture.service.start(fixture.assignmentId);
        var finalReportThread = new AtomicReference<String>();
        when(fixture.tutorService.nextDecisionStream(eq(fixture.assignment), any(), anyList())).thenReturn(Flux.just(
                TrainingAssignmentTutorService.AdaptiveTutorStreamEvent.completed(success())));
        when(fixture.tutorService.finalReport(eq(fixture.assignment), anyList(), any())).thenAnswer(_ -> {
            finalReportThread.set(Thread.currentThread().getName());
            return "Reporte basado en evidencia real";
        });

        var events = fixture.service.answerStream(
                fixture.assignmentId,
                "I understand the basics."
        ).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.getLast().assignment()).isNotNull();
        assertThat(finalReportThread.get()).contains("boundedElastic");
    }

    @Test
    void answerStreamRefetchesCanonicalAssignmentSnapshotAfterCompletion() {
        var assignmentId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();

        var loadedActivity = new TrainingActivity();
        ReflectionTestUtils.setField(loadedActivity, "id", activityId);
        ReflectionTestUtils.setField(loadedActivity, "title", "Pointers");
        ReflectionTestUtils.setField(loadedActivity, "status", TrainingActivityLifecycleStatus.PUBLISHED);

        var detachedActivity = new TrainingActivity();
        ReflectionTestUtils.setField(detachedActivity, "id", activityId);
        ReflectionTestUtils.setField(detachedActivity, "title", "Pointers");
        ReflectionTestUtils.setField(detachedActivity, "status", TrainingActivityLifecycleStatus.PUBLISHED);

        var canonicalActivity = new TrainingActivity();
        ReflectionTestUtils.setField(canonicalActivity, "id", activityId);
        ReflectionTestUtils.setField(canonicalActivity, "title", "Pointers");
        ReflectionTestUtils.setField(canonicalActivity, "status", TrainingActivityLifecycleStatus.PUBLISHED);

        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", memberId);

        var loadedAssignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(loadedAssignment, "id", assignmentId);
        ReflectionTestUtils.setField(loadedAssignment, "trainingActivity", loadedActivity);
        ReflectionTestUtils.setField(loadedAssignment, "groupClassMember", member);
        ReflectionTestUtils.setField(loadedAssignment, "status", TrainingActivityAssignmentStatus.STARTED);
        ReflectionTestUtils.setField(loadedAssignment, "assignedAt", Instant.now());
        ReflectionTestUtils.setField(loadedAssignment, "startedAt", Instant.now());
        ReflectionTestUtils.setField(loadedAssignment, "updatedAt", Instant.now());
        ReflectionTestUtils.setField(loadedAssignment, "currentQuestion", "¿Qué entiendes inicialmente de esta actividad?");
        ReflectionTestUtils.setField(loadedAssignment, "evaluationTranscript", "[]");
        ReflectionTestUtils.setField(loadedAssignment, "questionCount", 1);

        var detachedSavedAssignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(detachedSavedAssignment, "id", assignmentId);
        ReflectionTestUtils.setField(detachedSavedAssignment, "trainingActivity", detachedActivity);
        ReflectionTestUtils.setField(detachedSavedAssignment, "groupClassMember", member);
        ReflectionTestUtils.setField(detachedSavedAssignment, "status", TrainingActivityAssignmentStatus.STARTED);
        ReflectionTestUtils.setField(detachedSavedAssignment, "assignedAt", ReflectionTestUtils.getField(loadedAssignment, "assignedAt"));
        ReflectionTestUtils.setField(detachedSavedAssignment, "startedAt", ReflectionTestUtils.getField(loadedAssignment, "startedAt"));
        ReflectionTestUtils.setField(detachedSavedAssignment, "updatedAt", Instant.now());
        ReflectionTestUtils.setField(detachedSavedAssignment, "currentQuestion", "¿Qué parte necesita más claridad?");
        ReflectionTestUtils.setField(detachedSavedAssignment, "evaluationTranscript", "[]");
        ReflectionTestUtils.setField(detachedSavedAssignment, "questionCount", 2);

        var canonicalAssignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(canonicalAssignment, "id", assignmentId);
        ReflectionTestUtils.setField(canonicalAssignment, "trainingActivity", canonicalActivity);
        ReflectionTestUtils.setField(canonicalAssignment, "groupClassMember", member);
        ReflectionTestUtils.setField(canonicalAssignment, "status", TrainingActivityAssignmentStatus.STARTED);
        ReflectionTestUtils.setField(canonicalAssignment, "assignedAt", ReflectionTestUtils.getField(loadedAssignment, "assignedAt"));
        ReflectionTestUtils.setField(canonicalAssignment, "startedAt", ReflectionTestUtils.getField(loadedAssignment, "startedAt"));
        ReflectionTestUtils.setField(canonicalAssignment, "updatedAt", Instant.now());
        ReflectionTestUtils.setField(canonicalAssignment, "currentQuestion", "¿Qué parte necesita más claridad?");
        ReflectionTestUtils.setField(canonicalAssignment, "evaluationTranscript", "[{\"question\":\"¿Qué entiendes inicialmente de esta actividad?\",\"answer\":\"I understand the basics.\"}]");
        ReflectionTestUtils.setField(canonicalAssignment, "questionCount", 2);

        var repository = mock(TrainingActivityAssignmentRepository.class);
        when(repository.findWithTrainingActivityById(assignmentId)).thenReturn(
                Optional.of(loadedAssignment),
                Optional.of(loadedAssignment),
                Optional.of(canonicalAssignment));
        when(repository.findByTrainingActivity_IdOrderByUpdatedAtDesc(activityId)).thenReturn(List.of(canonicalAssignment));
        when(repository.save(loadedAssignment)).thenReturn(detachedSavedAssignment);

        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                memberId,
                groupClassId,
                GroupClassMemberKind.STUDENT));

        var tutorService = mock(TrainingAssignmentTutorService.class);
        when(tutorService.nextDecisionStream(eq(loadedAssignment), any(), anyList())).thenReturn(Flux.just(
                TrainingAssignmentTutorService.AdaptiveTutorStreamEvent.completed(question("¿Qué parte necesita más claridad?"))));
        when(tutorService.currentModelName()).thenReturn("test-model");
        when(tutorService.promptVersion()).thenReturn("test-prompt");

        var service = new TrainingAssignmentEvaluationService(
                repository,
                contextResolver,
                tutorService,
                new JsonMapper(),
                new TrainingAssignmentDecisionPersistenceService(
                        repository,
                        activityRepository,
                        tutorService,
                        mock(SafeBrowserAssignmentStateBus.class),
                        new JsonMapper()));

        var events = service.answerStream(assignmentId, "I understand the basics.").collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.getLast().assignment()).isSameAs(canonicalAssignment);
        assertThat(ReflectionTestUtils.getField(events.getLast().assignment(), "trainingActivity")).isSameAs(canonicalActivity);
    }

    @Test
    void answerStreamFallsBackToUiSafeSavedAssignmentWhenCanonicalReloadFails() {
        var assignmentId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        var memberId = UUID.randomUUID();

        var loadedActivity = new TrainingActivity();
        ReflectionTestUtils.setField(loadedActivity, "id", activityId);
        ReflectionTestUtils.setField(loadedActivity, "status", TrainingActivityLifecycleStatus.PUBLISHED);

        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", memberId);

        var loadedAssignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(loadedAssignment, "id", assignmentId);
        ReflectionTestUtils.setField(loadedAssignment, "trainingActivity", loadedActivity);
        ReflectionTestUtils.setField(loadedAssignment, "groupClassMember", member);
        ReflectionTestUtils.setField(loadedAssignment, "status", TrainingActivityAssignmentStatus.STARTED);
        ReflectionTestUtils.setField(loadedAssignment, "assignedAt", Instant.now());
        ReflectionTestUtils.setField(loadedAssignment, "startedAt", Instant.now());
        ReflectionTestUtils.setField(loadedAssignment, "updatedAt", Instant.now());
        ReflectionTestUtils.setField(loadedAssignment, "currentQuestion", "¿Qué entiendes inicialmente de esta actividad?");
        ReflectionTestUtils.setField(loadedAssignment, "evaluationTranscript", "[]");
        ReflectionTestUtils.setField(loadedAssignment, "questionCount", 1);

        var savedAssignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(savedAssignment, "id", assignmentId);
        ReflectionTestUtils.setField(savedAssignment, "trainingActivity", loadedActivity);
        ReflectionTestUtils.setField(savedAssignment, "groupClassMember", member);
        ReflectionTestUtils.setField(savedAssignment, "status", TrainingActivityAssignmentStatus.STARTED);
        ReflectionTestUtils.setField(savedAssignment, "assignedAt", ReflectionTestUtils.getField(loadedAssignment, "assignedAt"));
        ReflectionTestUtils.setField(savedAssignment, "startedAt", ReflectionTestUtils.getField(loadedAssignment, "startedAt"));
        ReflectionTestUtils.setField(savedAssignment, "updatedAt", Instant.now());
        ReflectionTestUtils.setField(savedAssignment, "currentQuestion", "¿Qué parte necesita más claridad?");
        ReflectionTestUtils.setField(savedAssignment, "evaluationTranscript", "[{\"question\":\"¿Qué entiendes inicialmente de esta actividad?\",\"answer\":\"I understand the basics.\"}]");
        ReflectionTestUtils.setField(savedAssignment, "questionCount", 2);

        var repository = mock(TrainingActivityAssignmentRepository.class);
        when(repository.findWithTrainingActivityById(assignmentId)).thenReturn(Optional.of(loadedAssignment), Optional.of(loadedAssignment))
                .thenThrow(new IllegalStateException("canonical reload failed"));
        when(repository.save(loadedAssignment)).thenReturn(savedAssignment);

        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                memberId,
                UUID.randomUUID(),
                GroupClassMemberKind.STUDENT));

        var tutorService = mock(TrainingAssignmentTutorService.class);
        when(tutorService.nextDecisionStream(eq(loadedAssignment), any(), anyList())).thenReturn(Flux.just(
                TrainingAssignmentTutorService.AdaptiveTutorStreamEvent.completed(question("¿Qué parte necesita más claridad?"))));
        when(tutorService.currentModelName()).thenReturn("test-model");
        when(tutorService.promptVersion()).thenReturn("test-prompt");

        var assignmentStateBus = mock(SafeBrowserAssignmentStateBus.class);
        var service = new TrainingAssignmentEvaluationService(
                repository,
                contextResolver,
                tutorService,
                new JsonMapper(),
                new TrainingAssignmentDecisionPersistenceService(
                        repository,
                        activityRepository,
                        tutorService,
                        assignmentStateBus,
                        new JsonMapper()));

        var events = service.answerStream(assignmentId, "I understand the basics.").collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.getLast().assignment()).isSameAs(savedAssignment);
        assertThat(events.getLast().assignment().getTrainingActivity().getStatus())
                .isEqualTo(TrainingActivityLifecycleStatus.PUBLISHED);
    }

    @Test
    void answerStreamPreparesAnswerBeforeSubscription() {
        var fixture = fixture();
        fixture.service.start(fixture.assignmentId);
        when(fixture.tutorService.nextDecisionStream(eq(fixture.assignment), any(), anyList())).thenReturn(Flux.never());
        var transcriptBefore = ReflectionTestUtils.getField(fixture.assignment, "evaluationTranscript");

        fixture.service.answerStream(fixture.assignmentId, "I understand the basics.");

        var transcriptCaptor = ArgumentCaptor.forClass(List.class);
        verify(fixture.tutorService).nextDecisionStream(eq(fixture.assignment), eq("I understand the basics."), transcriptCaptor.capture());
        @SuppressWarnings("unchecked")
        var transcript = (List<TrainingAssignmentEvaluationService.EvaluationExchange>) transcriptCaptor.getValue();
        assertThat(transcript).extracting(TrainingAssignmentEvaluationService.EvaluationExchange::answer)
                .containsExactly("I understand the basics.");
        assertThat(ReflectionTestUtils.getField(fixture.assignment, "evaluationTranscript")).isEqualTo(transcriptBefore);
    }

    @Test
    void answerStreamDoesNotOverwriteLockedAssignmentWhenCompletionArrivesLate() {
        var fixture = fixture();
        fixture.service.start(fixture.assignmentId);
        var sink = Sinks.many().unicast().<TrainingAssignmentTutorService.AdaptiveTutorStreamEvent>onBackpressureBuffer();
        when(fixture.tutorService.nextDecisionStream(eq(fixture.assignment), any(), anyList())).thenReturn(sink.asFlux());
        var transcriptBefore = ReflectionTestUtils.getField(fixture.assignment, "evaluationTranscript");

        var eventsMono = fixture.service.answerStream(fixture.assignmentId, "I understand the basics.").collectList();
        ReflectionTestUtils.setField(fixture.assignment, "safeBrowserLocked", true);
        ReflectionTestUtils.setField(fixture.assignment, "safeBrowserSessionActive", false);

        sink.tryEmitNext(TrainingAssignmentTutorService.AdaptiveTutorStreamEvent.completed(question("¿Qué parte necesita más claridad?")));
        sink.tryEmitComplete();

        var events = eventsMono.block();

        assertThat(events).isNotNull();
        var returnedAssignment = events.getLast().assignment();
        assertThat(returnedAssignment).isNotNull();
        assertThat(ReflectionTestUtils.getField(returnedAssignment, "safeBrowserLocked")).isEqualTo(true);
        assertThat((String) ReflectionTestUtils.getField(returnedAssignment, "currentQuestion"))
                .isEqualTo("¿Qué entiendes inicialmente de esta actividad?");
        assertThat(ReflectionTestUtils.getField(returnedAssignment, "questionCount")).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(returnedAssignment, "evaluationTranscript")).isEqualTo(transcriptBefore);
    }

    @Test
    void answerStreamCancellationBeforeCompletionDoesNotPersistTranscript() {
        var fixture = fixture();
        fixture.service.start(fixture.assignmentId);
        when(fixture.tutorService.nextDecisionStream(eq(fixture.assignment), any(), anyList())).thenReturn(Flux.never());
        var transcriptBefore = ReflectionTestUtils.getField(fixture.assignment, "evaluationTranscript");

        var subscription = fixture.service.answerStream(fixture.assignmentId, "I understand the basics.").subscribe();
        subscription.dispose();

        assertThat(ReflectionTestUtils.getField(fixture.assignment, "evaluationTranscript")).isEqualTo(transcriptBefore);
    }

    @Test
    void answerPersistsTranscriptAndUsesFallbackQuestionWhenTutorRecoveryHandlesBadOutput() {
        var fixture = fixture();
        when(fixture.tutorService.nextDecision(eq(fixture.assignment), any(), anyList()))
                .thenReturn(fallbackQuestion());
        fixture.service.start(fixture.assignmentId);

        var assignment = fixture.service.answer(
            fixture.assignmentId,
            "I need more practice with pointers."
        );

        assertThat(
            (String) ReflectionTestUtils.getField(
                assignment,
                "evaluationTranscript"
            )
        ).contains("I need more practice with pointers.");
        assertThat(ReflectionTestUtils.getField(assignment, "status")).isEqualTo(
            TrainingActivityAssignmentStatus.STARTED
        );
        assertThat(
            ReflectionTestUtils.getField(assignment, "questionCount")
        ).isEqualTo(2);
        assertThat(
            (String) ReflectionTestUtils.getField(assignment, "currentQuestion")
        ).contains("Modo de desarrollo");
    }

    @Test
    void answerPersistsBlankInputAndRequestsReconduction() {
        var fixture = fixture();
        fixture.service.start(fixture.assignmentId);

        var assignment = fixture.service.answer(fixture.assignmentId, "   ");

        assertThat((String) ReflectionTestUtils.getField(assignment, "evaluationTranscript"))
                .contains("\"answer\":\"\"");
        assertThat((String) ReflectionTestUtils.getField(assignment, "currentQuestion"))
                .isEqualTo("¿Qué parte necesita más claridad?");
    }

    @Test
    void answerDoesNotPersistRejectedTutorDecision() {
        var fixture = fixture();
        fixture.service.start(fixture.assignmentId);
        when(fixture.tutorService.nextDecision(eq(fixture.assignment), any(), anyList()))
                .thenThrow(new IllegalStateException("No fue posible continuar la tutoría"));
        var transcriptBefore = ReflectionTestUtils.getField(fixture.assignment, "evaluationTranscript");
        var currentQuestionBefore = ReflectionTestUtils.getField(fixture.assignment, "currentQuestion");
        var questionCountBefore = ReflectionTestUtils.getField(fixture.assignment, "questionCount");
        org.mockito.Mockito.clearInvocations(fixture.assignmentRepository);

        assertThatThrownBy(() -> fixture.service.answer(fixture.assignmentId, "I understand the basics."))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No fue posible continuar la tutoría");

        verify(fixture.assignmentRepository, never()).save(any());
        verify(fixture.tutorService, never()).finalReport(any(), anyList(), any());
        assertThat(ReflectionTestUtils.getField(fixture.assignment, "evaluationTranscript")).isEqualTo(transcriptBefore);
        assertThat(ReflectionTestUtils.getField(fixture.assignment, "currentQuestion")).isEqualTo(currentQuestionBefore);
        assertThat(ReflectionTestUtils.getField(fixture.assignment, "questionCount")).isEqualTo(questionCountBefore);
    }

    @Test
    void answerClosesPublishedActivityWhenAllAssignmentsAreTerminal() {
        var fixture = fixture();
        fixture.service.start(fixture.assignmentId);
        fixture.service.answer(fixture.assignmentId, "First answer");
        fixture.service.answer(fixture.assignmentId, "Second answer");
        verify(fixture.assignmentStateBus, never()).publish(any());
        ReflectionTestUtils.setField(fixture.assignment, "safeBrowserSessionActive", true);

        var assignment = fixture.service.answer(
            fixture.assignmentId,
            "Final answer"
        );

        assertThat(ReflectionTestUtils.getField(assignment, "status")).isEqualTo(
            TrainingActivityAssignmentStatus.SUBMITTED
        );
        var activity = (TrainingActivity) ReflectionTestUtils.getField(assignment, "trainingActivity");
        assertThat(ReflectionTestUtils.getField(activity, "status")).isEqualTo(
            TrainingActivityLifecycleStatus.CLOSED
        );
        assertThat(ReflectionTestUtils.getField(activity, "closesAt")).isNotNull();
        assertThat(ReflectionTestUtils.getField(assignment, "finalReport")).isEqualTo("Reporte basado en evidencia real");
        assertThat(ReflectionTestUtils.getField(assignment, "safeBrowserSessionActive")).isEqualTo(false);
        verify(fixture.tutorService).finalReport(eq(fixture.assignment), anyList(), any());
        var trainingActivity = (TrainingActivity) ReflectionTestUtils.getField(
            assignment,
            "trainingActivity"
        );
        var groupClassMember = (com.wornux.data.entities.academic.GroupClassMember) ReflectionTestUtils.getField(
            assignment,
            "groupClassMember"
        );
        var captor = ArgumentCaptor.forClass(
            SafeBrowserAssignmentStateBus.Notification.class
        );
        verify(fixture.assignmentStateBus).publish(captor.capture());
        var notification = captor.getValue();
        assertThat(notification.trainingActivityId()).isEqualTo(
            (UUID) ReflectionTestUtils.getField(trainingActivity, "id")
        );
        assertThat(notification.assignmentId()).isEqualTo(
            (UUID) ReflectionTestUtils.getField(assignment, "id")
        );
        assertThat(notification.groupClassMemberId()).isEqualTo(
            (UUID) ReflectionTestUtils.getField(groupClassMember, "id")
        );
        assertThat(notification.affectsTrainingActivity(
            (UUID) ReflectionTestUtils.getField(trainingActivity, "id")
        )).isTrue();
        assertThat(
            notification.affectsAssignment((UUID) ReflectionTestUtils.getField(assignment, "id"))
        ).isTrue();
        assertThat(notification.locked()).isEqualTo(
            (Boolean) ReflectionTestUtils.getField(assignment, "safeBrowserLocked")
        );
        verify(fixture.activityRepository).save(activity);
    }

    @Test
    void answerRejectsExpiredAssignment() {
        var fixture = fixture();
        ReflectionTestUtils.setField(fixture.assignment, "status", TrainingActivityAssignmentStatus.EXPIRED);

        assertThatThrownBy(() ->
            fixture.service.answer(fixture.assignmentId, "Too late")
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The evaluation assignment has ended.");
    }

    private static Fixture fixture() {
        var assignmentId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();

        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", activityId);
        ReflectionTestUtils.setField(activity, "title", "Pointers");
        ReflectionTestUtils.setField(
            activity,
            "status",
            TrainingActivityLifecycleStatus.PUBLISHED
        );

        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", memberId);

        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", assignmentId);
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "groupClassMember", member);
        ReflectionTestUtils.setField(
            assignment,
            "status",
            TrainingActivityAssignmentStatus.ASSIGNED
        );
        ReflectionTestUtils.setField(assignment, "assignedAt", Instant.now());
        ReflectionTestUtils.setField(assignment, "updatedAt", Instant.now());

        var repository = mock(TrainingActivityAssignmentRepository.class);
        when(repository.findWithTrainingActivityById(assignmentId)).thenReturn(
            Optional.of(assignment)
        );
        when(
            repository.findByTrainingActivity_IdOrderByUpdatedAtDesc(
                activityId
            )
        ).thenAnswer(invocation -> List.of(assignment));
        when(repository.save(assignment)).thenAnswer(invocation ->
            invocation.getArgument(0)
        );
        when(repository.saveAndFlush(assignment)).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.save(activity)).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(
            new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                memberId,
                groupClassId,
                GroupClassMemberKind.STUDENT
            )
        );

        var tutorService = mock(TrainingAssignmentTutorService.class);
        when(tutorService.firstDecision(assignment)).thenReturn(question("¿Qué entiendes inicialmente de esta actividad?"));
        when(tutorService.nextDecision(eq(assignment), any(), anyList()))
                .thenReturn(question("¿Qué parte necesita más claridad?"))
                .thenReturn(question("¿Puedes justificarlo con un ejemplo concreto?"))
                .thenReturn(success());
        when(tutorService.nextDecisionStream(eq(assignment), any(), anyList())).thenReturn(Flux.just(
                TrainingAssignmentTutorService.AdaptiveTutorStreamEvent.completed(question("¿Qué parte necesita más claridad?"))));
        when(tutorService.finalReport(eq(assignment), anyList(), any())).thenReturn("Reporte basado en evidencia real");
        when(tutorService.currentModelName()).thenReturn("test-model");
        when(tutorService.promptVersion()).thenReturn("test-prompt");

        var assignmentStateBus = mock(SafeBrowserAssignmentStateBus.class);
        var service = new TrainingAssignmentEvaluationService(
            repository,
            contextResolver,
            tutorService,
            new JsonMapper(),
            new TrainingAssignmentDecisionPersistenceService(
                    repository,
                    activityRepository,
                    tutorService,
                    assignmentStateBus,
                    new JsonMapper())
        );
        return new Fixture(
            service,
            repository,
            activityRepository,
            tutorService,
            assignmentStateBus,
            assignment,
            assignmentId
        );
    }

    private static AdaptiveTutorDecision fallbackQuestion() {
        return new AdaptiveTutorDecision(
                TutorDecisionType.QUESTION,
                AnswerQuality.TOO_VAGUE,
                EvidenceStatus.WEAK_EVIDENCE,
                CoverageStatus.WEAK,
                PedagogicalMove.ASK_FOR_CLARITY,
                true,
                List.of(),
                List.of("Clearer explanation or concrete example"),
                false,
                "Modo de desarrollo: pide una evidencia concreta del tema y una breve justificación en la misma respuesta.",
                "The adaptive tutor model did not return a usable next decision.");
    }

    private static AdaptiveTutorDecision question(String questionText) {
        return new AdaptiveTutorDecision(
                TutorDecisionType.QUESTION,
                AnswerQuality.GOOD,
                EvidenceStatus.PARTIAL_EVIDENCE,
                CoverageStatus.PARTIAL,
                PedagogicalMove.ASK_FOR_CLARITY,
                true,
                List.of(),
                List.of("example"),
                false,
                questionText,
                "Needs one more question.");
    }

    private static AdaptiveTutorDecision success() {
        return new AdaptiveTutorDecision(
                TutorDecisionType.COMPLETE_SUCCESS,
                AnswerQuality.EXCELLENT,
                EvidenceStatus.STRONG_EVIDENCE,
                CoverageStatus.SUFFICIENT,
                PedagogicalMove.COMPLETE_SUCCESSFULLY,
                false,
                List.of("understanding", "example"),
                List.of(),
                false,
                "",
                "Sufficient evidence.");
    }

    private record Fixture(
        TrainingAssignmentEvaluationService service,
        TrainingActivityAssignmentRepository assignmentRepository,
        TrainingActivityRepository activityRepository,
        TrainingAssignmentTutorService tutorService,
        SafeBrowserAssignmentStateBus assignmentStateBus,
        TrainingActivityAssignment assignment,
        UUID assignmentId
    ) {}
}
