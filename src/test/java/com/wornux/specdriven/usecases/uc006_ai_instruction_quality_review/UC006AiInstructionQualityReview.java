package com.wornux.specdriven.usecases.uc006_ai_instruction_quality_review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.ComponentUtil;
import com.wornux.config.ApplicationProperties;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAiJob;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobStatus;
import com.wornux.data.entities.training_activity.instruction_review.TrainingInstructionReviewExecutionStatus;
import com.wornux.data.entities.training_activity.instruction_review.TrainingInstructionReview;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewCacheEntry;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAiJobRepository;
import com.wornux.data.repositories.training_activity.instruction_review.InstructionReviewCacheRepository;
import com.wornux.data.repositories.training_activity.instruction_review.TrainingInstructionReviewOverrideRepository;
import com.wornux.data.repositories.training_activity.instruction_review.TrainingInstructionReviewRepository;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.email.EmailService;
import com.wornux.services.email.EmailTemplateService;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.SafeBrowserModeService;
import com.wornux.services.training_activity.TrainingActivityLaunchedBus;
import com.wornux.services.training_activity.TrainingActivitySaveCommand;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.services.training_activity.instruction_review.InstructionLintIssueDto;
import com.wornux.services.training_activity.instruction_review.InstructionQualityReviewException;
import com.wornux.services.training_activity.instruction_review.InstructionReviewCoordinator;
import com.wornux.services.training_activity.instruction_review.InstructionReviewModelOutputException;
import com.wornux.services.training_activity.instruction_review.InstructionReviewResult;
import com.wornux.services.training_activity.instruction_review.InstructionReviewService;
import com.wornux.services.training_activity.instruction_review.InstructionReviewSnapshotDto;
import com.wornux.services.training_activity.instruction_review.AdvisoryInstructionReviewService;
import com.wornux.services.training_activity.instruction_review.InstructionReviewJobWorker;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.training_activity.TrainingActivityView;
import com.wornux.ui.training_activity.instruction_review.InstructionLinterEditor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.test.util.ReflectionTestUtils;

class UC006AiInstructionQualityReview {

    @Test
    void br11_duplicateReviewRequestCreatesOneDurableJobWithoutCallingTheModel() {
        var reviewRepository = mock(TrainingInstructionReviewRepository.class);
        var overrideRepository = mock(TrainingInstructionReviewOverrideRepository.class);
        var jobRepository = mock(TrainingActivityAiJobRepository.class);
        var engine = mock(InstructionReviewService.class);
        var candidateId = UUID.randomUUID();
        when(engine.hashNormalizedInstructions(any())).thenReturn("instruction-hash");
        when(engine.currentModelName()).thenReturn("review-model");
        when(engine.promptVersion()).thenReturn("rubric-v1");
        var persistedReview = new TrainingInstructionReview();
        persistedReview.setExecutionStatus(TrainingInstructionReviewExecutionStatus.PENDING);
        persistedReview.setInstructionsHash("instruction-hash");
        persistedReview.setRequestedAt(Instant.now());
        when(reviewRepository.findByCandidateIdAndGroupClass_IdAndRequestedByGroupClassMember_IdAndInstructionsHashAndModelNameAndRubricVersion(
                any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty(), Optional.of(persistedReview));
        when(reviewRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        var service = new AdvisoryInstructionReviewService(reviewRepository, overrideRepository, jobRepository, engine);

        var requested = service.request(candidateId, null, UUID.randomUUID(), UUID.randomUUID(), "Pointers", goodInstructions());

        assertThat(requested.reviewStatus()).isEqualTo(InstructionReviewStatus.REVIEWING);
        verify(reviewRepository).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(jobRepository).save(any(TrainingActivityAiJob.class));
        verify(engine, never()).review(any(), any());
    }

    @Test
    void af7_duplicateReviewRequestReusesPersistedCandidateAndDoesNotScheduleAnotherJob() {
        var reviewRepository = mock(TrainingInstructionReviewRepository.class);
        var overrideRepository = mock(TrainingInstructionReviewOverrideRepository.class);
        var jobRepository = mock(TrainingActivityAiJobRepository.class);
        var engine = mock(InstructionReviewService.class);
        var candidateId = UUID.randomUUID();
        var review = new TrainingInstructionReview();
        review.setCandidateId(candidateId);
        review.setInstructionsHash("instruction-hash");
        review.setExecutionStatus(TrainingInstructionReviewExecutionStatus.PENDING);
        review.setRequestedAt(Instant.now());
        when(engine.hashNormalizedInstructions(any())).thenReturn("instruction-hash");
        when(engine.currentModelName()).thenReturn("review-model");
        when(engine.promptVersion()).thenReturn("rubric-v1");
        when(reviewRepository.findByCandidateIdAndGroupClass_IdAndRequestedByGroupClassMember_IdAndInstructionsHashAndModelNameAndRubricVersion(
                any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(review));
        var service = new AdvisoryInstructionReviewService(reviewRepository, overrideRepository, jobRepository, engine);

        var requested = service.request(candidateId, null, UUID.randomUUID(), UUID.randomUUID(), "Pointers", goodInstructions());

        assertThat(requested.reviewStatus()).isEqualTo(InstructionReviewStatus.REVIEWING);
        verify(jobRepository, never()).save(any(TrainingActivityAiJob.class));
        verify(engine, never()).review(any(), any());
    }

    @Test
    void br11_idempotencyConflictRereadsTheScopedReviewWithoutSchedulingAnotherJob() {
        var reviewRepository = mock(TrainingInstructionReviewRepository.class);
        var overrideRepository = mock(TrainingInstructionReviewOverrideRepository.class);
        var jobRepository = mock(TrainingActivityAiJobRepository.class);
        var engine = mock(InstructionReviewService.class);
        var review = new TrainingInstructionReview();
        review.setInstructionsHash("instruction-hash");
        review.setExecutionStatus(TrainingInstructionReviewExecutionStatus.PENDING);
        review.setRequestedAt(Instant.now());
        when(engine.hashNormalizedInstructions(any())).thenReturn("instruction-hash");
        when(engine.currentModelName()).thenReturn("review-model");
        when(engine.promptVersion()).thenReturn("rubric-v1");
        when(reviewRepository.findByCandidateIdAndGroupClass_IdAndRequestedByGroupClassMember_IdAndInstructionsHashAndModelNameAndRubricVersion(
                any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty(), Optional.of(review));
        when(reviewRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        new AdvisoryInstructionReviewService(reviewRepository, overrideRepository, jobRepository, engine)
                .request(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), "Pointers", goodInstructions());

        verify(jobRepository, never()).save(any(TrainingActivityAiJob.class));
    }

    @Test
    void af4_expiredRunningLeaseCanBeClaimedAgain() {
        var reviewRepository = mock(TrainingInstructionReviewRepository.class);
        var jobRepository = mock(TrainingActivityAiJobRepository.class);
        var job = new TrainingActivityAiJob();
        var review = new TrainingInstructionReview();
        review.setId(UUID.randomUUID());
        review.setTitleSnapshot("Pointers");
        review.setInstructionsSnapshot(goodInstructions());
        job.setId(UUID.randomUUID());
        job.setStatus(TrainingActivityAiJobStatus.RUNNING);
        job.setLeaseUntil(Instant.now().minusSeconds(1));
        job.setInstructionReview(review);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.claim(any(), any(), any(), any(), any())).thenReturn(1);

        var work = new AdvisoryInstructionReviewService(
                reviewRepository, mock(TrainingInstructionReviewOverrideRepository.class), jobRepository, mock(InstructionReviewService.class))
                .claim(job.getId(), Instant.now(), Instant.now().plusSeconds(30));

        assertThat(work.jobId()).isEqualTo(job.getId());
    }

    @Test
    void af4_modelTimeoutInterruptsTheOutstandingModelFuture() throws InterruptedException {
        var reviewService = mock(AdvisoryInstructionReviewService.class);
        var reviewEngine = mock(InstructionReviewService.class);
        var workerExecutor = executor();
        var modelExecutor = executor();
        var meterRegistry = new SimpleMeterRegistry();
        var interrupted = new CountDownLatch(1);
        try {
            var work = new AdvisoryInstructionReviewService.ReviewWork(UUID.randomUUID(), UUID.randomUUID(), "Pointers", goodInstructions());
            when(reviewService.claim(any(), any(), any())).thenReturn(work);
            when(reviewService.applyFailure(work.jobId(), "MODEL_TIMEOUT")).thenReturn(true);
            when(reviewEngine.review(any(), any())).thenAnswer(_ -> {
                try {
                    Thread.sleep(10_000);
                }
                catch (InterruptedException exception) {
                    interrupted.countDown();
                    throw new IllegalStateException(exception);
                }
                return reviewResult(InstructionQualityStatus.GOOD);
            });
            var worker = new InstructionReviewJobWorker(
                    reviewService, reviewEngine, workerExecutor, modelExecutor, 10, 30, meterRegistry);

            ReflectionTestUtils.invokeMethod(worker, "process", work.jobId());

            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
            verify(reviewService).applyFailure(work.jobId(), "MODEL_TIMEOUT");
            assertThat(meterRegistry.get("training.activity.instruction-review.timeout").counter().count()).isEqualTo(1);
            assertThat(meterRegistry.get("training.activity.instruction-review.error").counter().count()).isEqualTo(1);
            assertThat(meterRegistry.get("training.activity.instruction-review.retry").counter().count()).isEqualTo(1);
            assertThat(meterRegistry.get("training.activity.instruction-review.model.latency").timer().count()).isEqualTo(1);
            assertThat(meterRegistry.find("training.activity.instruction-review.worker.queue.depth").gauge()).isNotNull();
            assertThat(meterRegistry.find("training.activity.instruction-review.model.queue.depth").gauge()).isNotNull();
        }
        finally {
            workerExecutor.shutdownNow();
            modelExecutor.shutdownNow();
        }
    }

    @Test
    void af1_readOperationsRequireTrainingActivityViewPermission() throws NoSuchMethodException {
        assertThat(TrainingActivityService.class.getMethod("listAll").getAnnotation(RequiresPermission.class).value())
                .isEqualTo(AppPermission.TRAINING_ACTIVITY_VIEW);
        assertThat(TrainingActivityService.class.getMethod("get", UUID.class).getAnnotation(RequiresPermission.class).value())
                .isEqualTo(AppPermission.TRAINING_ACTIVITY_VIEW);
        assertThat(TrainingActivityService.class.getMethod("listAssignments", UUID.class)
                .getAnnotation(RequiresPermission.class).value()).isEqualTo(AppPermission.TRAINING_ACTIVITY_VIEW);
    }

    @Test
    void br11_discardsPersistedIssueRangesOutsideTheReviewedInstructions() {
        var reviewRepository = mock(TrainingInstructionReviewRepository.class);
        var engine = mock(InstructionReviewService.class);
        var review = new TrainingInstructionReview();
        review.setExecutionStatus(TrainingInstructionReviewExecutionStatus.SUCCEEDED);
        review.setOutcome(com.wornux.data.entities.training_activity.instruction_review.InstructionReviewOutcome.GOOD);
        review.setInstructionsHash("instruction-hash");
        review.setInstructionsSnapshot("short text");
        review.setRequestedAt(Instant.now());
        review.setIssuesJson("""
                [{"id":"bad","severity":"WARNING","category":"TEST","startOffset":-1,"endOffset":3,"suggestedReplacement":"replacement"}]
                """);
        when(engine.hashNormalizedInstructions(any())).thenReturn("instruction-hash");
        when(reviewRepository.findFirstByTrainingActivity_IdOrderByRequestedAtDesc(any())).thenReturn(Optional.of(review));

        var snapshot = new AdvisoryInstructionReviewService(
                reviewRepository, mock(TrainingInstructionReviewOverrideRepository.class), mock(TrainingActivityAiJobRepository.class), engine)
                .current(UUID.randomUUID(), "short text");

        assertThat(snapshot.issues()).isEmpty();
    }

    @Test
    void br01_updateRejectsFabricatedOrStaleOverrideConfirmationBeforePersisting() {
        var activityRepository = mock(TrainingActivityRepository.class);
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var advisory = mock(AdvisoryInstructionReviewService.class);
        var activity = activity(goodInstructions());
        var context = professorContext(activity.getGroupClass().getId());
        when(contextResolver.requireCurrent()).thenReturn(context);
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(advisory.request(any(), any(), any(), any(), any(), any())).thenReturn(new InstructionReviewSnapshotDto(
                activity.getId(), "current-hash", InstructionReviewStatus.COMPLETED,
                InstructionQualityStatus.NEEDS_IMPROVEMENT, false, "Needs work", false, false, List.of(), "", Instant.now()));
        var service = new TrainingActivityService(
                activityRepository,
                mock(TrainingActivityAssignmentRepository.class),
                mock(GroupClassMemberRepository.class),
                mock(EmailService.class),
                mock(EmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                mock(InstructionReviewCoordinator.class),
                advisory,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.update(activity.getId(), new TrainingActivitySaveCommand(
                        "Updated title", goodInstructions(), false, "fabricated-hash", UUID.randomUUID())))
                .isInstanceOf(InstructionQualityReviewException.class);

        verify(activityRepository, never()).save(any(TrainingActivity.class));
    }

    @Test
    void br01_updateDoesNotPersistWhenTheReviewRequestFails() {
        var activityRepository = mock(TrainingActivityRepository.class);
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var advisory = mock(AdvisoryInstructionReviewService.class);
        var activity = activity(goodInstructions());
        when(contextResolver.requireCurrent()).thenReturn(professorContext(activity.getGroupClass().getId()));
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(advisory.request(any(), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("review unavailable"));
        var service = new TrainingActivityService(
                activityRepository,
                mock(TrainingActivityAssignmentRepository.class),
                mock(GroupClassMemberRepository.class),
                mock(EmailService.class),
                mock(EmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                mock(InstructionReviewCoordinator.class),
                advisory,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.update(activity.getId(), new TrainingActivitySaveCommand(
                        "Updated title", goodInstructions(), false, "", UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("review unavailable");

        verify(activityRepository, never()).save(any(TrainingActivity.class));
        assertThat(activity.getTitle()).isEqualTo("Strings en C");
    }

    @Test
    void af4_retryableModelFailureUsesBoundedRetryWithoutMutatingTheReviewVerdict() {
        var reviewRepository = mock(TrainingInstructionReviewRepository.class);
        var overrideRepository = mock(TrainingInstructionReviewOverrideRepository.class);
        var jobRepository = mock(TrainingActivityAiJobRepository.class);
        var engine = mock(InstructionReviewService.class);
        var review = new TrainingInstructionReview();
        var job = new TrainingActivityAiJob();
        job.setStatus(TrainingActivityAiJobStatus.RUNNING);
        job.setAttemptCount(1);
        job.setMaxAttempts(3);
        job.setInstructionReview(review);
        when(jobRepository.findById(any())).thenReturn(Optional.of(job));
        var service = new AdvisoryInstructionReviewService(reviewRepository, overrideRepository, jobRepository, engine);

        service.applyFailure(UUID.randomUUID(), "MODEL_TIMEOUT");

        assertThat(job.getStatus()).isEqualTo(TrainingActivityAiJobStatus.RETRYABLE);
        assertThat(review.getExecutionStatus()).isNull();
    }

    @Test
    void saveDraftDoesNotWaitForModel() {
        var fixture = serviceFixture();
        when(fixture.contextResolver.requireCurrent()).thenReturn(professorContext(UUID.randomUUID()));
        var decision = new InstructionReviewCoordinator.ReviewBeforeSaveDecision(
                new InstructionReviewSnapshotDto(
                        null,
                        "hash-1",
                        InstructionReviewStatus.READY_TO_SAVE,
                        InstructionQualityStatus.GOOD,
                        true,
                        "Las instrucciones están listas para usarse.",
                        false,
                        false,
                        List.of(),
                        "",
                        Instant.now()),
                reviewResult(InstructionQualityStatus.GOOD),
                true);
        when(fixture.coordinator.reviewBeforeSave(any(), any(), any())).thenReturn(decision);
        when(fixture.activityRepository.save(any(TrainingActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = fixture.service.createPending(new TrainingActivitySaveCommand("Strings", goodInstructions(), false, ""));

        assertThat(saved.getStatus()).isEqualTo(TrainingActivityLifecycleStatus.DRAFT);
        verify(fixture.activityRepository).save(any(TrainingActivity.class));
    }

    @Test
    void requestReviewReturnsLocalInvalidWhenInstructionsBlank() {
        var fixture = coordinatorFixture(unusedModel());
        var activity = activity("   ");

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().reviewStatus()).isEqualTo(InstructionReviewStatus.LOCAL_INVALID);
        assertThat(prepared.canSave()).isFalse();
    }

    @Test
    void requestReviewReturnsSkippedNoChangesWhenHashMatchesAndLastStatusCompleted() {
        var fixture = coordinatorFixture(unusedModel());
        var activity = activity(goodInstructions());
        var reviewHash = fixture.reviewService.reviewHash(activity.getTitle(), activity.getInstructions());
        activity.setInstructionReviewHash(reviewHash);
        activity.setInstructionReviewInstructionsHash(reviewHash);
        activity.setInstructionReviewStatus(InstructionReviewStatus.COMPLETED);
        activity.setInstructionReviewQualityStatus(InstructionQualityStatus.GOOD);
        activity.setInstructionReviewValidInstruction(true);
        activity.setInstructionReviewPromptVersion(fixture.reviewService.promptVersion());
        activity.setInstructionReviewModelName(fixture.reviewService.currentModelName());

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().reviewStatus()).isEqualTo(InstructionReviewStatus.READY_TO_SAVE);
        assertThat(prepared.canSave()).isTrue();
    }

    @Test
    void requestReviewDoesNotSkipWhenHashMatchesButLastStatusFailed() {
        var fixture = coordinatorFixture(modelReturning(goodJson()));
        var activity = activity(goodInstructions());
        var reviewHash = fixture.reviewService.reviewHash(activity.getTitle(), activity.getInstructions());
        activity.setInstructionReviewHash(reviewHash);
        activity.setInstructionReviewInstructionsHash(reviewHash);
        activity.setInstructionReviewStatus(InstructionReviewStatus.FAILED);

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().reviewStatus()).isEqualTo(InstructionReviewStatus.READY_TO_SAVE);
        assertThat(prepared.canSave()).isTrue();
    }

    @Test
    void requestReviewReturnsCompletedFromCacheWhenHashExists() {
        var fixture = coordinatorFixture(unusedModel());
        var activity = activity(goodInstructions());
        var reviewHash = fixture.reviewService.reviewHash(activity.getTitle(), activity.getInstructions());
        var cacheEntry = new InstructionReviewCacheEntry();
        cacheEntry.setReviewHash(reviewHash);
        cacheEntry.setPromptVersion(fixture.reviewService.promptVersion());
        cacheEntry.setModelName(fixture.reviewService.currentModelName());
        cacheEntry.setReviewStatus(InstructionReviewStatus.COMPLETED);
        cacheEntry.setQualityStatus(InstructionQualityStatus.GOOD);
        cacheEntry.setValidInstruction(true);
        cacheEntry.setIssuesJson("[]");
        cacheEntry.setReviewMessage("Las instrucciones están listas para usarse.");
        cacheEntry.setCompletedAt(Instant.now());
        when(fixture.cacheRepository.findById(reviewHash)).thenReturn(Optional.of(cacheEntry));

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().reviewStatus()).isEqualTo(InstructionReviewStatus.COMPLETED_FROM_CACHE);
        assertThat(prepared.snapshot().qualityStatus()).isEqualTo(InstructionQualityStatus.GOOD);
        assertThat(prepared.canSave()).isTrue();
    }

    @Test
    void requestReviewCallsModelWhenNoHashAndNoCache() {
        var fixture = coordinatorFixture(modelReturning(cleanGoodJson()));
        var activity = activity(goodInstructions());
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(fixture.activityRepository.save(any(TrainingActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var snapshot = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions()).snapshot();

        assertThat(snapshot.reviewStatus()).isEqualTo(InstructionReviewStatus.READY_TO_SAVE);
        assertThat(snapshot.qualityStatus()).isEqualTo(InstructionQualityStatus.GOOD);
    }

    @Test
    void requestReviewReturnsNeedsImprovementWithWholeRangeHighlightWhenInstructionsAreTooShort() {
        var fixture = coordinatorFixture(unusedModel());
        var activity = activity("Revisa strings");

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().qualityStatus()).isEqualTo(InstructionQualityStatus.NEEDS_IMPROVEMENT);
        assertThat(prepared.snapshot().issues()).hasSize(1);
        assertThat(prepared.snapshot().issues().getFirst().startOffset()).isEqualTo(0);
        assertThat(prepared.snapshot().issues().getFirst().endOffset()).isEqualTo(activity.getInstructions().length());
        assertThat(prepared.snapshot().issues().getFirst().suggestedReplacement()).isBlank();
        assertThat(prepared.snapshot().recreatedInstructions()).isBlank();
    }

    @Test
    void falsePremiseCInstructionIsNeedsImprovement() {
        var fixture = coordinatorFixture(unusedModel());
        var instructions = "Observa este fragmento de código en C:\n\nfor (int i = 0; i < 10; i++) {\n    printf(\"Hola %d\\n\", i);\n}\n\n¿Qué error de "
                + "sintaxis tiene este bucle y cómo lo corregirías?";
        var activity = activity(instructions);

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().qualityStatus()).isEqualTo(InstructionQualityStatus.NEEDS_IMPROVEMENT);
        assertThat(prepared.snapshot().canSave()).isFalse();
        assertThat(prepared.snapshot().issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("FALSE_PREMISE");
            assertThat(issue.severity()).isEqualTo("ERROR");
            assertThat(issue.suggestedReplacement())
                    .isEqualTo("Observa el bucle y explica por qué compila correctamente; luego compara qué ocurriría si se elimina un paréntesis, una llave o un punto y coma.");
        });
    }

    @Test
    void requestReviewReturnsGoodWithSingleInlineSuggestion() {
        var fixture = coordinatorFixture(modelReturning(goodJson()));
        var activity = activity(goodInstructions());

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().reviewStatus()).isEqualTo(InstructionReviewStatus.READY_TO_SAVE);
        assertThat(prepared.snapshot().qualityStatus()).isEqualTo(InstructionQualityStatus.GOOD);
        assertThat(prepared.snapshot().issues()).hasSize(1);
        assertThat(prepared.snapshot().issues().getFirst().startOffset()).isEqualTo(0);
        assertThat(prepared.snapshot().issues().getFirst().endOffset()).isEqualTo(50);
        assertThat(prepared.snapshot().issues().getFirst().suggestedReplacement())
                .isEqualTo("Pide explicación, ejemplo y justificación sobre strlen y strcmp.");
        assertThat(prepared.snapshot().recreatedInstructions()).isBlank();
    }

    @Test
    void appliedSuggestionRequiresFreshReviewInsteadOfSilentCacheReuse() {
        var cachedReviews = new HashMap<String, InstructionReviewCacheEntry>();
        var fixture = coordinatorFixture(modelReturning(goodJson()), cachedReviews);
        var activity = activity(goodInstructions());

        var sourceSnapshot = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions()).snapshot();
        var issue = sourceSnapshot.issues().getFirst();
        var acceptedInstructions = issue.suggestedReplacement()
                + activity.getInstructions().substring(issue.endOffset());
        var acceptedSnapshot = fixture.coordinator
                .reviewBeforeSave(activity(acceptedInstructions), activity.getTitle(), acceptedInstructions)
                .snapshot();
        assertThat(acceptedSnapshot.reviewStatus()).isEqualTo(InstructionReviewStatus.READY_TO_SAVE);
        assertThat(acceptedSnapshot.issues()).isNotEmpty();
        assertThat(acceptedSnapshot.fromCache()).isFalse();
    }

    @Test
    void saveDraftAllowsGoodSuggestionWithoutConfirmation() {
        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.save(any(TrainingActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var cacheRepository = mock(InstructionReviewCacheRepository.class);
        when(cacheRepository.findById(any())).thenReturn(Optional.empty());

        var reviewService = new InstructionReviewService(modelReturning(goodJson()));
        ReflectionTestUtils.setField(reviewService, "modelName", "test-model");
        var coordinator = new InstructionReviewCoordinator(activityRepository, cacheRepository, reviewService);

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var groupClassId = UUID.randomUUID();
        when(contextResolver.requireCurrent()).thenReturn(professorContext(groupClassId));

        var service = new TrainingActivityService(
                activityRepository,
                mock(TrainingActivityAssignmentRepository.class),
                mock(GroupClassMemberRepository.class),
                mock(EmailService.class),
                mock(EmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                coordinator,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        var saved = service.createPending(new TrainingActivitySaveCommand("Strings", goodInstructions(), true, ""));

        assertThat(saved.getInstructionReviewStatus()).isEqualTo(InstructionReviewStatus.COMPLETED);
        assertThat(saved.getInstructionReviewQualityStatus()).isEqualTo(InstructionQualityStatus.GOOD);
        assertThat(saved.isSafeBrowserEnabled()).isTrue();
    }

    @Test
    void unavailableReviewDoesNotSaveDraft() {
        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.save(any(TrainingActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var cacheRepository = mock(InstructionReviewCacheRepository.class);
        when(cacheRepository.findById(any())).thenReturn(Optional.empty());

        var reviewService = new InstructionReviewService(modelFailing(new IllegalStateException("boom")));
        ReflectionTestUtils.setField(reviewService, "modelName", "test-model");
        var coordinator = new InstructionReviewCoordinator(activityRepository, cacheRepository, reviewService);

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var groupClassId = UUID.randomUUID();
        when(contextResolver.requireCurrent()).thenReturn(professorContext(groupClassId));

        var service = new TrainingActivityService(
                activityRepository,
                mock(TrainingActivityAssignmentRepository.class),
                mock(GroupClassMemberRepository.class),
                mock(EmailService.class),
                mock(EmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                coordinator,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.createPending(new TrainingActivitySaveCommand("Strings", goodInstructions(), false, "")))
                .isInstanceOf(InstructionQualityReviewException.class)
                .hasMessageContaining("No pudimos completar la revisión automática");
    }

    @Test
    void mainFlow_appliedSuggestionGoodNoIssuesPersistsOnceAndIgnoresQueuedDuplicateClick() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var service = mock(TrainingActivityService.class);
            var instructions = goodInstructions();
            var saved = activity(instructions);
            var snapshot = new InstructionReviewSnapshotDto(
                    null,
                    "accepted-review-hash",
                    InstructionReviewStatus.READY_TO_SAVE,
                    InstructionQualityStatus.GOOD,
                    true,
                    "Las instrucciones están listas para usarse.",
                    false,
                    false,
                    List.of(),
                    "",
                    Instant.now());
            when(service.listAll()).thenReturn(List.of(), List.of(saved));
            when(service.getInstructionReviewSnapshot(saved.getId())).thenReturn(snapshot);

            var view = new TrainingActivityView(
                    service,
                    mock(SafeBrowserModeService.class),
                    mock(SafeBrowserAssignmentStateBus.class),
                    mock(WorkspaceRoutingService.class),
                    mock(AuthenticatedUserContextUtils.class));
            UI.setCurrent(ui);
            var titleField = (TextField) ReflectionTestUtils.getField(view, "titleField");
            var instructionField = (InstructionLinterEditor) ReflectionTestUtils.getField(view, "instructionField");
            var saveButton = (Button) ReflectionTestUtils.getField(view, "saveButton");
            titleField.setValue("Strings en C");
            instructionField.setValue(instructions);
            ReflectionTestUtils.setField(view, "lastReviewSnapshot", snapshot);
            ReflectionTestUtils.setField(view, "lastReviewedTitle", "Strings en C");
            ReflectionTestUtils.setField(view, "lastReviewedInstructions", instructions);

            var reentered = new AtomicBoolean();
            when(service.createPending(any(TrainingActivitySaveCommand.class))).thenAnswer(_ -> {
                if (reentered.compareAndSet(false, true)) {
                    ReflectionTestUtils.invokeMethod(view, "onSave");
                }
                return saved;
            });

            ReflectionTestUtils.invokeMethod(view, "onSave");

            verify(service).createPending(any(TrainingActivitySaveCommand.class));
            verify(service, never()).reviewDraft(any(TrainingActivitySaveCommand.class));
            assertThat(saveButton.isEnabled()).isTrue();
            assertThat(saveButton.getText()).isEqualTo("Guardar borrador");
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void mainFlow_unavailableReviewBlocksSave() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var service = mock(TrainingActivityService.class);
            var instructions = goodInstructions();
            var saved = activity(instructions);
            var snapshot = unavailableSnapshot("review-hash-unavailable");
            when(service.listAll()).thenReturn(List.of(), List.of(saved));
            when(service.reviewDraft(any(TrainingActivitySaveCommand.class))).thenReturn(snapshot);
            when(service.createPending(any(TrainingActivitySaveCommand.class))).thenReturn(saved);
            when(service.getInstructionReviewSnapshot(saved.getId())).thenReturn(snapshot);

            var view = new TrainingActivityView(
                    service,
                    mock(SafeBrowserModeService.class),
                    mock(SafeBrowserAssignmentStateBus.class),
                    mock(WorkspaceRoutingService.class),
                    mock(AuthenticatedUserContextUtils.class));
            UI.setCurrent(ui);
            var titleField = (TextField) ReflectionTestUtils.getField(view, "titleField");
            var instructionField = (InstructionLinterEditor) ReflectionTestUtils.getField(view, "instructionField");
            var saveButton = (Button) ReflectionTestUtils.getField(view, "saveButton");
            titleField.setValue("Strings en C");
            instructionField.setValue(instructions);

            ReflectionTestUtils.invokeMethod(view, "onSave");

            verify(service).reviewDraft(any(TrainingActivitySaveCommand.class));
            verify(service, never()).createPending(any(TrainingActivitySaveCommand.class));
            assertThat(saveButton.getText()).isEqualTo("Guardar borrador");
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void saveDraftDoesNotPersistCachedReviewWithVisibleIssues() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var service = mock(TrainingActivityService.class);
            var snapshot = reviewSnapshotWithSuggestion(
                    "review-hash-cached-good",
                    InstructionReviewStatus.COMPLETED_FROM_CACHE,
                    InstructionQualityStatus.GOOD);
            when(service.listAll()).thenReturn(List.of());
            when(service.reviewDraft(any(TrainingActivitySaveCommand.class))).thenReturn(snapshot);

            var view = new TrainingActivityView(
                    service,
                    mock(SafeBrowserModeService.class),
                    mock(SafeBrowserAssignmentStateBus.class),
                    mock(WorkspaceRoutingService.class),
                    mock(AuthenticatedUserContextUtils.class));
            UI.setCurrent(ui);
            var titleField = (TextField) ReflectionTestUtils.getField(view, "titleField");
            var instructionField = (InstructionLinterEditor) ReflectionTestUtils.getField(view, "instructionField");
            titleField.setValue("Strings en C");
            instructionField.setValue(goodInstructions());

            ReflectionTestUtils.invokeMethod(view, "onSave");

            verify(service).reviewDraft(any(TrainingActivitySaveCommand.class));
            verify(service, never()).createPending(any(TrainingActivitySaveCommand.class));
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void mainFlow_goodReviewWaitsForExplicitDraftSave() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var service = mock(TrainingActivityService.class);
            var snapshot = reviewSnapshotWithSuggestion(
                    "review-hash-good",
                    InstructionReviewStatus.READY_TO_SAVE,
                    InstructionQualityStatus.GOOD);
            when(service.listAll()).thenReturn(List.of());
            when(service.reviewDraft(any(TrainingActivitySaveCommand.class))).thenReturn(snapshot);

            var view = new TrainingActivityView(
                    service,
                    mock(SafeBrowserModeService.class),
                    mock(SafeBrowserAssignmentStateBus.class),
                    mock(WorkspaceRoutingService.class),
                    mock(AuthenticatedUserContextUtils.class));
            UI.setCurrent(ui);
            var titleField = (TextField) ReflectionTestUtils.getField(view, "titleField");
            var instructionField = (InstructionLinterEditor) ReflectionTestUtils.getField(view, "instructionField");
            titleField.setValue("Strings en C");
            instructionField.setValue(goodInstructions());

            ReflectionTestUtils.invokeMethod(view, "onSave");

            verify(service).reviewDraft(any(TrainingActivitySaveCommand.class));
            verify(service, never()).createPending(any(TrainingActivitySaveCommand.class));
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void mainFlow_needsImprovementStillBlocksSave() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var service = mock(TrainingActivityService.class);
            when(service.listAll()).thenReturn(List.of());
            when(service.reviewDraft(any(TrainingActivitySaveCommand.class))).thenReturn(reviewSnapshotWithSuggestion(
                    "review-hash-fix",
                    InstructionReviewStatus.COMPLETED_FROM_CACHE,
                    InstructionQualityStatus.NEEDS_IMPROVEMENT));

            var view = new TrainingActivityView(
                    service,
                    mock(SafeBrowserModeService.class),
                    mock(SafeBrowserAssignmentStateBus.class),
                    mock(WorkspaceRoutingService.class),
                    mock(AuthenticatedUserContextUtils.class));
            UI.setCurrent(ui);
            var titleField = (TextField) ReflectionTestUtils.getField(view, "titleField");
            var instructionField = (InstructionLinterEditor) ReflectionTestUtils.getField(view, "instructionField");
            var saveButton = (Button) ReflectionTestUtils.getField(view, "saveButton");
            titleField.setValue("Strings en C");
            instructionField.setValue(goodInstructions());

            ReflectionTestUtils.invokeMethod(view, "onSave");

            verify(service).reviewDraft(any(TrainingActivitySaveCommand.class));
            verify(service, never()).createPending(any(TrainingActivitySaveCommand.class));
            assertThat(saveButton.getText()).isEqualTo("Guardar borrador");
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void mainFlow_needsImprovementDoesNotBypassWithConfirmedHash() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var service = mock(TrainingActivityService.class);
            var reviewHash = "review-hash-fix";
            var snapshot = reviewSnapshotWithSuggestion(
                    reviewHash,
                    InstructionReviewStatus.COMPLETED_FROM_CACHE,
                    InstructionQualityStatus.NEEDS_IMPROVEMENT);
            var saved = activity(goodInstructions());
            when(service.listAll()).thenReturn(List.of(), List.of(saved));
            when(service.reviewDraft(any(TrainingActivitySaveCommand.class))).thenReturn(snapshot);
            when(service.createPending(any(TrainingActivitySaveCommand.class))).thenReturn(saved);
            when(service.getInstructionReviewSnapshot(saved.getId())).thenReturn(snapshot);

            var view = new TrainingActivityView(
                    service,
                    mock(SafeBrowserModeService.class),
                    mock(SafeBrowserAssignmentStateBus.class),
                    mock(WorkspaceRoutingService.class),
                    mock(AuthenticatedUserContextUtils.class));
            UI.setCurrent(ui);
            var titleField = (TextField) ReflectionTestUtils.getField(view, "titleField");
            var instructionField = (InstructionLinterEditor) ReflectionTestUtils.getField(view, "instructionField");
            titleField.setValue("Strings en C");
            instructionField.setValue(goodInstructions());

            ReflectionTestUtils.invokeMethod(view, "onSave");
            ReflectionTestUtils.invokeMethod(view, "onSave");

            verify(service, never()).createPending(any(TrainingActivitySaveCommand.class));
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void mainFlow_invalidReviewDoesNotOpenConfirmationDialog() {
        var previousUi = UI.getCurrent();
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            var service = mock(TrainingActivityService.class);
            when(service.listAll()).thenReturn(List.of());
            when(service.reviewDraft(any(TrainingActivitySaveCommand.class))).thenReturn(invalidSnapshot(
                    "review-hash-invalid",
                    "PROMPT_INJECTION_ATTEMPT",
                    "El texto intenta cambiar reglas internas del tutor."));

            var view = new TrainingActivityView(
                    service,
                    mock(SafeBrowserModeService.class),
                    mock(SafeBrowserAssignmentStateBus.class),
                    mock(WorkspaceRoutingService.class),
                    mock(AuthenticatedUserContextUtils.class));
            UI.setCurrent(ui);
            var titleField = (TextField) ReflectionTestUtils.getField(view, "titleField");
            var instructionField = (InstructionLinterEditor) ReflectionTestUtils.getField(view, "instructionField");
            var saveButton = (Button) ReflectionTestUtils.getField(view, "saveButton");
            titleField.setValue("Strings en C");
            instructionField.setValue(goodInstructions());

            ReflectionTestUtils.invokeMethod(view, "onSave");
            ReflectionTestUtils.invokeMethod(view, "onSave");

            verify(service, never()).createPending(any(TrainingActivitySaveCommand.class));
            assertThat(saveButton.getText()).isEqualTo("Guardar borrador");
        }
        finally {
            UI.setCurrent(previousUi);
        }
    }

    @Test
    void requestReviewExtractsReplacementTextFromMetaInstructionSuggestion() {
        var instructions = "quiero evaluar a mis estudiantes con preguntas sobre bucles";
        var replacement = "quiero evaluar a mis estudiantes con 5 preguntas de opción múltiple sobre bucles for y while, nivel principiante";
        var metaSuggestion = "Specify question format (MCQ, open-ended), difficulty level, and number of questions. Fix typo: 'estudiantes'. Example: '%s'"
                .formatted(replacement);
        var fixture = coordinatorFixture(modelReturning(goodJsonWithSuggestion(metaSuggestion, instructions.length())));
        var activity = activity(instructions);

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().qualityStatus()).isEqualTo(InstructionQualityStatus.GOOD);
        assertThat(prepared.snapshot().issues()).hasSize(1);
        assertThat(prepared.snapshot().issues().getFirst().suggestedReplacement()).isEqualTo(replacement);
        assertThat(prepared.snapshot().issues().getFirst().suggestedReplacement()).doesNotContain("Specify question format");
    }

    @Test
    void requestReviewExpandsWholeInstructionReplacementWhenSuggestionStartsWithOriginalInstructions() {
        var instructions = "quiero hacer una evaluación sobre el manejo de strings en C";
        var replacement = instructions
                + ", incluyendo operaciones básicas como concatenación, búsqueda, longitud y manipulación de cadenas";
        var fixture = coordinatorFixture(modelReturning(goodJsonWithSuggestedReplacementAndOffsets(
                replacement,
                0,
                50)));
        var activity = activity(instructions);

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().issues()).hasSize(1);
        assertThat(prepared.snapshot().issues().getFirst().startOffset()).isEqualTo(0);
        assertThat(prepared.snapshot().issues().getFirst().endOffset()).isEqualTo(instructions.length());
        assertThat(prepared.snapshot().issues().getFirst().suggestedReplacement()).isEqualTo(replacement);
    }

    @Test
    void requestReviewNormalizesExactEndCursorWholeRewriteIntoWholeInstructionReplacement() {
        var instructions = "quiero que se hagan preguntas sobre arreglos";
        var replacement = instructions + ", como recorrerlos, acceder a ellos e introducir valores.";
        var fixture = coordinatorFixture(modelReturning(goodJsonWithSuggestedReplacementAndOffsets(
                replacement,
                instructions.length(),
                instructions.length())));
        var activity = activity(instructions);

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().qualityStatus()).isEqualTo(InstructionQualityStatus.GOOD);
        assertThat(prepared.snapshot().issues()).hasSize(1);
        assertThat(prepared.snapshot().issues().getFirst().startOffset()).isEqualTo(0);
        assertThat(prepared.snapshot().issues().getFirst().endOffset()).isEqualTo(instructions.length());
        assertThat(prepared.snapshot().issues().getFirst().suggestedReplacement()).isEqualTo(replacement);
    }

    @Test
    void requestReviewTreatsFullSentenceSuggestionWithPartialRangeAsWholeInstructionReplacement() {
        var cachedReviews = new HashMap<String, InstructionReviewCacheEntry>();
        var instructions = loggedRangeInstructions();
        var replacement = loggedRangeReplacement();
        var fixture = coordinatorFixture(modelReturning(goodJsonWithSuggestedReplacementAndOffsets(
                replacement,
                34,
                76)), cachedReviews);
        var activity = activity(instructions);

        var snapshot = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions()).snapshot();
        var issue = snapshot.issues().getFirst();
        var acceptedInstructions = instructions.substring(0, issue.startOffset())
                + issue.suggestedReplacement()
                + instructions.substring(issue.endOffset());

        assertThat(instructions).hasSize(80);
        assertThat(snapshot.reviewStatus()).isEqualTo(InstructionReviewStatus.READY_TO_SAVE);
        assertThat(issue.startOffset()).isZero();
        assertThat(issue.endOffset()).isEqualTo(instructions.length());
        assertThat(issue.suggestedReplacement()).isEqualTo(replacement);
        assertThat(acceptedInstructions).isEqualTo(replacement);
        assertThat(fixture.coordinator
                .reviewBeforeSave(activity(acceptedInstructions), activity.getTitle(), acceptedInstructions)
                .snapshot()
                .issues()).isNotEmpty();
    }

    @Test
    void requestReviewRejectsExactEndCursorInsertionWhenSuggestionIsNotAWholeRewrite() {
        var instructions = "quiero que se hagan preguntas sobre arreglos";
        var fixture = coordinatorFixture(modelReturning(goodJsonWithSuggestedReplacementAndOffsets(
                " y ejercicios cortos",
                instructions.length(),
                instructions.length())));

        assertThatThrownBy(() -> fixture.coordinator
                        .reviewBeforeSave(activity(instructions), "Strings en C", instructions))
                .isInstanceOf(InstructionReviewModelOutputException.class);
    }

    @Test
    void requestReviewPrefersReplacementOnlyFieldOverLegacySuggestion() {
        var instructions = "quiero evaluar a mis estudiantes con preguntas sobre bucles";
        var replacement = "quiero evaluar a mis estudiantes con 5 preguntas de opción múltiple sobre bucles for y while, nivel principiante";
        var fixture = coordinatorFixture(modelReturning(goodJsonWithSuggestedReplacement(
                replacement,
                "Specify question format and add an example.",
                instructions.length())));
        var activity = activity(instructions);

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().qualityStatus()).isEqualTo(InstructionQualityStatus.GOOD);
        assertThat(prepared.snapshot().issues()).hasSize(1);
        assertThat(prepared.snapshot().issues().getFirst().suggestedReplacement()).isEqualTo(replacement);
    }

    @Test
    void requestReviewExtractsReplacementFromCommonLabel() {
        var instructions = "quiero evaluar a mis estudiantes con preguntas sobre bucles";
        var replacement = "quiero evaluar a mis estudiantes con 5 preguntas de opción múltiple sobre bucles for y while, nivel principiante";
        var fixture = coordinatorFixture(modelReturning(goodJsonWithSuggestedReplacement(
                "Suggested replacement: " + replacement,
                "",
                instructions.length())));
        var activity = activity(instructions);

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().issues()).hasSize(1);
        assertThat(prepared.snapshot().issues().getFirst().suggestedReplacement()).isEqualTo(replacement);
        assertThat(prepared.snapshot().issues().getFirst().suggestedReplacement()).doesNotContain("Suggested replacement");
    }

    @Test
    void requestReviewOmitsAdviceOnlyReplacementLabel() {
        var instructions = "Ask students to explain loops with examples and justify each answer.";
        var fixture = coordinatorFixture(modelReturning(goodJsonWithSuggestedReplacement(
                "Suggested replacement: Specify question format and difficulty level.",
                "",
                instructions.length())));
        var activity = activity(instructions);

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().issues()).hasSize(1);
        assertThat(prepared.snapshot().issues().getFirst().suggestedReplacement()).isBlank();
    }

    @Test
    void requestReviewOmitsInlineSuggestionWhenOffsetsAreOutsideInstructionBounds() {
        var instructions = "quiero evaluar a mis estudiantes con preguntas sobre bucles";
        var replacement = "quiero evaluar a mis estudiantes con 5 preguntas de opción múltiple sobre bucles for y while, nivel principiante";
        var negativeStart = coordinatorFixture(modelReturning(goodJsonWithSuggestedReplacementAndOffsets(
                replacement,
                -3,
                10)));
        var outOfRangeEnd = coordinatorFixture(modelReturning(goodJsonWithSuggestedReplacementAndOffsets(
                replacement,
                0,
                instructions.length() + 5)));

        var negativeStartIssue = negativeStart.coordinator
                .reviewBeforeSave(activity(instructions), "Strings en C", instructions)
                .snapshot()
                .issues()
                .getFirst();
        var outOfRangeEndIssue = outOfRangeEnd.coordinator
                .reviewBeforeSave(activity(instructions), "Strings en C", instructions)
                .snapshot()
                .issues()
                .getFirst();

        assertThat(negativeStartIssue.startOffset()).isNull();
        assertThat(negativeStartIssue.endOffset()).isNull();
        assertThat(negativeStartIssue.suggestedReplacement()).isBlank();
        assertThat(outOfRangeEndIssue.startOffset()).isNull();
        assertThat(outOfRangeEndIssue.endOffset()).isNull();
        assertThat(outOfRangeEndIssue.suggestedReplacement()).isBlank();
    }

    @Test
    void requestReviewUsesReplacementTextAliasWhenSuggestedReplacementIsMissing() {
        var instructions = "quiero evaluar a mis estudiantes con preguntas sobre bucles";
        var replacement = "quiero evaluar a mis estudiantes con 5 preguntas de opción múltiple sobre bucles for y while, nivel principiante";
        var fixture = coordinatorFixture(modelReturning(goodJsonWithReplacementText(
                replacement,
                "Specify question format and add an example.",
                instructions.length())));
        var activity = activity(instructions);

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().issues()).hasSize(1);
        assertThat(prepared.snapshot().issues().getFirst().suggestedReplacement()).isEqualTo(replacement);
    }

    @Test
    void requestReviewReturnsNeedsImprovementWithSingleGeneralIssueWhenModelSaysSo() {
        var fixture = coordinatorFixture(modelReturning(needsImprovementJson()));
        var activity = activity(goodInstructions());

        var prepared = fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions());

        assertThat(prepared.snapshot().qualityStatus()).isEqualTo(InstructionQualityStatus.NEEDS_IMPROVEMENT);
        assertThat(prepared.snapshot().issues()).hasSize(1);
        assertThat(prepared.snapshot().issues().getFirst().startOffset()).isNull();
        assertThat(prepared.snapshot().issues().getFirst().endOffset()).isNull();
        assertThat(prepared.snapshot().issues().getFirst().suggestedReplacement()).isBlank();
        assertThat(prepared.snapshot().recreatedInstructions()).isBlank();
    }

    @Test
    void modelOutputFailureIsNotReportedAsUnavailable() {
        var fixture = coordinatorFixture(modelReturning("not-json"));
        var activity = activity(goodInstructions());

        assertThatThrownBy(() -> fixture.coordinator.reviewBeforeSave(activity, activity.getTitle(), activity.getInstructions()))
                .isInstanceOf(InstructionReviewModelOutputException.class);
    }

    @Test
    void launchBlocksWhenReviewMissing() {
        var fixture = serviceFixture();
        var activity = activity(goodInstructions());
        when(fixture.contextResolver.requireCurrent()).thenReturn(professorContext(activity.getGroupClass().getId()));
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(fixture.activityRepository.findFirstByCreatedByTenantAccount_IdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(fixture.activityRepository.save(any(TrainingActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.assignmentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.groupClassMemberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(any())).thenReturn(List.of(studentMember()));

        assertThatThrownBy(() -> fixture.service.launch(activity.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revisión actual GOOD");
    }

    @Test
    void launchBlocksWhenReviewHashIsStale() {
        var fixture = serviceFixture();
        var activity = activity(goodInstructions());
        activity.setInstructionReviewStatus(InstructionReviewStatus.SKIPPED_NO_CHANGES);
        when(fixture.contextResolver.requireCurrent()).thenReturn(professorContext(activity.getGroupClass().getId()));
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(fixture.activityRepository.findFirstByCreatedByTenantAccount_IdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(fixture.activityRepository.save(any(TrainingActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.assignmentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.groupClassMemberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(any())).thenReturn(List.of(studentMember()));

        assertThatThrownBy(() -> fixture.service.launch(activity.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revisión actual GOOD");
    }

    @Test
    void launchAllowsWhenReviewIsCurrentGoodAndHashMatches() {
        var fixture = serviceFixture();
        var activity = activity(goodInstructions());
        var tenantAccount = new TenantAccount();
        tenantAccount.setId(activity.getCreatedByTenantAccount().getId());
        activity.setCreatedByTenantAccount(tenantAccount);
        when(fixture.contextResolver.requireCurrent()).thenReturn(professorContext(activity.getGroupClass().getId()));
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(fixture.activityRepository.findFirstByCreatedByTenantAccount_IdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(fixture.activityRepository.save(any(TrainingActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.assignmentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.groupClassMemberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(any())).thenReturn(List.of(studentMember()));
        when(fixture.coordinator.hasCurrentGoodInstructionReview(activity)).thenReturn(true);

        var launched = fixture.service.launch(activity.getId());

        assertThat(launched).isEqualTo(1);
    }

    private static ServiceFixture serviceFixture() {
        var activityRepository = mock(TrainingActivityRepository.class);
        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        var groupClassMemberRepository = mock(GroupClassMemberRepository.class);
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var coordinator = mock(InstructionReviewCoordinator.class);
        var service = new TrainingActivityService(
                activityRepository,
                assignmentRepository,
                groupClassMemberRepository,
                mock(EmailService.class),
                mock(EmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                coordinator,
                null);
        ReflectionTestUtils.setField(service, "self", service);
        return new ServiceFixture(service, activityRepository, assignmentRepository, groupClassMemberRepository, contextResolver, coordinator);
    }

    private static CoordinatorFixture coordinatorFixture(ChatModel chatModel) {
        return coordinatorFixture(chatModel, new HashMap<>());
    }

    private static CoordinatorFixture coordinatorFixture(
            ChatModel chatModel,
            Map<String, InstructionReviewCacheEntry> cachedReviews) {
        var activityRepository = mock(TrainingActivityRepository.class);
        var cacheRepository = mock(InstructionReviewCacheRepository.class);
        when(cacheRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(cachedReviews.get(invocation.getArgument(0))));
        when(cacheRepository.save(any(InstructionReviewCacheEntry.class))).thenAnswer(invocation -> {
            var entry = invocation.getArgument(0, InstructionReviewCacheEntry.class);
            cachedReviews.put(entry.getReviewHash(), entry);
            return entry;
        });
        var reviewService = new InstructionReviewService(chatModel);
        ReflectionTestUtils.setField(reviewService, "modelName", "test-model");
        var coordinator = new InstructionReviewCoordinator(activityRepository, cacheRepository, reviewService);
        return new CoordinatorFixture(coordinator, reviewService, activityRepository, cacheRepository);
    }

    private static TrainingActivity activity(String instructions) {
        var groupClass = new GroupClass();
        groupClass.setId(UUID.randomUUID());
        var tenantAccount = new TenantAccount();
        tenantAccount.setId(UUID.randomUUID());
        var activity = new TrainingActivity();
        activity.setId(UUID.randomUUID());
        activity.setGroupClass(groupClass);
        activity.setCreatedByTenantAccount(tenantAccount);
        activity.setTitle("Strings en C");
        activity.setInstructions(instructions);
        activity.setStatus(TrainingActivityLifecycleStatus.DRAFT);
        activity.setCreatedAt(Instant.now());
        activity.setUpdatedAt(Instant.now());
        return activity;
    }

    private static ActiveAcademicContext professorContext(UUID groupClassId) {
        return new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), groupClassId, GroupClassMemberKind.PROFESSOR);
    }

    private static com.wornux.data.entities.academic.GroupClassMember studentMember() {
        var account = new com.wornux.data.entities.identity.Account();
        account.setEmail("student@example.com");
        account.setFirstName("Student");
        account.setLastName("Test");

        var tenantAccount = new TenantAccount();
        tenantAccount.setAccount(account);

        var member = new com.wornux.data.entities.academic.GroupClassMember();
        member.setId(UUID.randomUUID());
        member.setMemberKind(GroupClassMemberKind.STUDENT);
        member.setTenantAccount(tenantAccount);
        return member;
    }

    private static String goodInstructions() {
        return "Evalúa si el estudiante comprende strings en C, incluyendo arreglos de char, terminador nulo, lectura con scanf/fgets y comparación con strcmp. El tutor debe hacer preguntas socráticas progresivas, pedir explicación y evidencia.";
    }

    private static String loggedRangeInstructions() {
        return "quiero que hagas preguntas sobre bucles, enfocadas en el recorrido y depuración.";
    }

    private static String loggedRangeReplacement() {
        return "quiero que hagas preguntas sobre bucles, enfocadas en el recorrido";
    }

    private static ChatModel modelReturning(String json) {
        return _ -> response(json);
    }

    private static ChatModel modelFailing(RuntimeException exception) {
        return _ -> {
            throw exception;
        };
    }

    private static ChatModel unusedModel() {
        return _ -> {
            throw new AssertionError("This scenario must not call the model");
        };
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static InstructionReviewResult reviewResult(InstructionQualityStatus qualityStatus) {
        return new InstructionReviewResult(
                qualityStatus != null,
                qualityStatus,
                qualityStatus == InstructionQualityStatus.GOOD,
                qualityStatus == InstructionQualityStatus.GOOD,
                "message",
                "message",
                List.of(),
                "",
                "",
                "hash",
                Instant.now(),
                "test-model",
                "uc-006-v9-compact-fast-review");
    }

    private static InstructionReviewSnapshotDto unavailableSnapshot(String reviewHash) {
        return new InstructionReviewSnapshotDto(
                null,
                reviewHash,
                InstructionReviewStatus.UNAVAILABLE,
                null,
                false,
                "No pudimos completar la revisión automática de instrucciones. Intenta guardar de nuevo.",
                true,
                false,
                List.of(),
                "",
                Instant.now());
    }

    private static InstructionReviewSnapshotDto reviewSnapshotWithSuggestion(
            String reviewHash,
            InstructionReviewStatus reviewStatus,
            InstructionQualityStatus qualityStatus) {
        var cachedGoodWithVisibleSuggestion = reviewStatus == InstructionReviewStatus.COMPLETED_FROM_CACHE
                && qualityStatus == InstructionQualityStatus.GOOD;
        return new InstructionReviewSnapshotDto(
                null,
                reviewHash,
                reviewStatus,
                qualityStatus,
                qualityStatus == InstructionQualityStatus.GOOD && !cachedGoodWithVisibleSuggestion,
                qualityStatus == InstructionQualityStatus.NEEDS_IMPROVEMENT
                        ? "La instrucción es demasiado vaga para guiar al tutor."
                        : "La instrucción es usable, pero conviene precisar la evidencia esperada.",
                true,
                reviewStatus == InstructionReviewStatus.COMPLETED_FROM_CACHE,
                List.of(new InstructionLintIssueDto(
                        "issue-1",
                        qualityStatus == InstructionQualityStatus.NEEDS_IMPROVEMENT ? "MISSING_EXPECTED_EVIDENCE" : "OPTIONAL_REFINEMENT",
                        "WARNING",
                        0,
                        50,
                        qualityStatus == InstructionQualityStatus.NEEDS_IMPROVEMENT
                                ? "La instrucción es demasiado vaga para guiar al tutor."
                                : "La instrucción es usable, pero conviene precisar la evidencia esperada.",
                        "",
                        "Pide explicación, ejemplo y justificación sobre strlen y strcmp.",
                        "")),
                "",
                Instant.now());
    }

    private static InstructionReviewSnapshotDto invalidSnapshot(
            String reviewHash,
            String issueCode,
            String message) {
        return new InstructionReviewSnapshotDto(
                null,
                reviewHash,
                InstructionReviewStatus.COMPLETED_FROM_CACHE,
                null,
                false,
                message,
                false,
                true,
                List.of(new InstructionLintIssueDto(
                        "issue-invalid",
                        issueCode,
                        "ERROR",
                        null,
                        null,
                        message,
                        "",
                        "",
                        "")),
                "",
                Instant.now());
    }

    private static String goodJson() {
        return """
                {
                  "analysisType": "GOOD",
                  "analysis": "La instrucción es usable, pero puede precisar mejor qué evidencia debe demostrar el estudiante.",
                  "suggestion": "Pide explicación, ejemplo y justificación sobre strlen y strcmp.",
                  "startOffset": 0,
                  "endOffset": 50
                }
                """;
    }

    private static String cleanGoodJson() {
        return """
                {
                  "analysisType": "GOOD",
                  "analysis": "La instrucción define tema, propósito y evidencia esperada con suficiente claridad.",
                  "suggestion": null,
                  "startOffset": null,
                  "endOffset": null
                }
                """;
    }

    private static String goodJsonWithSuggestion(String suggestion, int endOffset) {
        return """
                {
                  "analysisType": "GOOD",
                  "analysis": "La instrucción es usable, pero puede precisar mejor qué evidencia debe demostrar el estudiante.",
                  "suggestion": %s,
                  "startOffset": 0,
                  "endOffset": %d
                }
                """.formatted(jsonString(suggestion), endOffset);
    }

    private static String goodJsonWithSuggestedReplacement(String suggestedReplacement, String suggestion, int endOffset) {
        return """
                {
                  "analysisType": "GOOD",
                  "analysis": "La instrucción es usable, pero puede precisar mejor qué evidencia debe demostrar el estudiante.",
                  "suggestedReplacement": %s,
                  "suggestion": %s,
                  "startOffset": 0,
                  "endOffset": %d
                }
                """.formatted(jsonString(suggestedReplacement), jsonString(suggestion), endOffset);
    }

    private static String goodJsonWithSuggestedReplacementAndOffsets(
            String suggestedReplacement,
            int startOffset,
            int endOffset) {
        return """
                {
                  "analysisType": "GOOD",
                  "analysis": "La instrucción es usable, pero puede precisar mejor qué evidencia debe demostrar el estudiante.",
                  "suggestedReplacement": %s,
                  "startOffset": %d,
                  "endOffset": %d
                }
                """.formatted(jsonString(suggestedReplacement), startOffset, endOffset);
    }

    private static String goodJsonWithReplacementText(String replacementText, String suggestion, int endOffset) {
        return """
                {
                  "analysisType": "GOOD",
                  "analysis": "La instrucción es usable, pero puede precisar mejor qué evidencia debe demostrar el estudiante.",
                  "replacementText": %s,
                  "suggestion": %s,
                  "startOffset": 0,
                  "endOffset": %d
                }
                """.formatted(jsonString(replacementText), jsonString(suggestion), endOffset);
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Button confirmDialogButton(Dialog dialog, String text) {
        var footerComponents = new java.util.ArrayList<com.vaadin.flow.component.Component>();
        ComponentUtil.findComponents(dialog.getFooter().getElement(), footerComponents::add);
        return footerComponents.stream()
                .flatMap(UC006AiInstructionQualityReview::componentTree)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static java.util.stream.Stream<com.vaadin.flow.component.Component> componentTree(
            com.vaadin.flow.component.Component component) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(component),
                component.getChildren().flatMap(UC006AiInstructionQualityReview::componentTree));
    }

    private static String needsImprovementJson() {
        return """
                {
                  "analysisType": "NEEDS_IMPROVEMENT",
                  "analysis": "La instrucción es demasiado vaga para guiar preguntas socráticas útiles.",
                  "suggestion": null,
                  "startOffset": null,
                  "endOffset": null
                }
                """;
    }

    private static ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2));
    }

    private record ServiceFixture(
            TrainingActivityService service,
            TrainingActivityRepository activityRepository,
            TrainingActivityAssignmentRepository assignmentRepository,
            GroupClassMemberRepository groupClassMemberRepository,
            ActiveAcademicContextResolver contextResolver,
            InstructionReviewCoordinator coordinator) {
    }

    private record CoordinatorFixture(
            InstructionReviewCoordinator coordinator,
            InstructionReviewService reviewService,
            TrainingActivityRepository activityRepository,
            InstructionReviewCacheRepository cacheRepository) {
    }

    private static ApplicationProperties applicationProperties() {
        var properties = new ApplicationProperties();
        properties.getEmail().setInvitationBaseUrl("http://localhost:3321");
        return properties;
    }
}
