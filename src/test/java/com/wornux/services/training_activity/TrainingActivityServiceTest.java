package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.config.ApplicationProperties;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.infrastructure.email.SmtpEmailService;
import com.wornux.infrastructure.email.ThymeleafEmailTemplateService;
import com.wornux.services.training_activity.instruction_review.InstructionLintIssueDto;
import com.wornux.services.training_activity.instruction_review.InstructionReviewCoordinator;
import com.wornux.services.training_activity.instruction_review.InstructionReviewExecutionStatus;
import com.wornux.services.training_activity.instruction_review.InstructionReviewResult;
import com.wornux.services.training_activity.instruction_review.InstructionReviewSnapshotDto;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.services.training_activity.instruction_review.InstructionQualityReviewException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class TrainingActivityServiceTest {

    @Test
    void createPendingBlocksUnavailableReview() {
        var groupClassId = UUID.randomUUID();
        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.save(anyTrainingActivity())).thenAnswer(invocation -> invocation.getArgument(0));

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(
                new ActiveAcademicContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        groupClassId,
                        GroupClassMemberKind.PROFESSOR));

        var coordinator = mock(InstructionReviewCoordinator.class);
        when(coordinator.reviewBeforeSave(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(unavailableAdvisoryDecision("review-hash-unavailable"));

        var service = new TrainingActivityService(
                activityRepository,
                mock(TrainingActivityAssignmentRepository.class),
                mock(GroupClassMemberRepository.class),
                mock(SmtpEmailService.class),
                mock(ThymeleafEmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                coordinator,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.createPending(new TrainingActivitySaveCommand("Strings", validInstructions(), false, "")))
                .isInstanceOf(InstructionQualityReviewException.class)
                .hasMessageContaining("No pudimos completar la revisión automática");
        verify(activityRepository, never()).save(anyTrainingActivity());
    }

    @Test
    void updateBlocksUnavailableReview() {
        var activityId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();
        var activity = draftActivity(activityId, groupClassId);

        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(activityRepository.save(activity)).thenAnswer(invocation -> invocation.getArgument(0));

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(
                new ActiveAcademicContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        groupClassId,
                        GroupClassMemberKind.PROFESSOR));

        var coordinator = mock(InstructionReviewCoordinator.class);
        when(coordinator.reviewBeforeSave(org.mockito.ArgumentMatchers.eq(activity), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(unavailableAdvisoryDecision("review-hash-update"));

        var service = new TrainingActivityService(
                activityRepository,
                mock(TrainingActivityAssignmentRepository.class),
                mock(GroupClassMemberRepository.class),
                mock(SmtpEmailService.class),
                mock(ThymeleafEmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                coordinator,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.update(activityId, new TrainingActivitySaveCommand("Nuevo título", validInstructions(), false, "")))
                .isInstanceOf(InstructionQualityReviewException.class)
                .hasMessageContaining("No pudimos completar la revisión automática");
        verify(activityRepository, never()).save(activity);
    }

    @Test
    void createPendingRejectsNeedsImprovementWhenNotGood() {
        var groupClassId = UUID.randomUUID();
        var activityRepository = mock(TrainingActivityRepository.class);

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(
                new ActiveAcademicContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        groupClassId,
                        GroupClassMemberKind.PROFESSOR));

        var coordinator = mock(InstructionReviewCoordinator.class);
        when(coordinator.reviewBeforeSave(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(needsUserFixDecision("review-hash-fix"));

        var service = new TrainingActivityService(
                activityRepository,
                mock(TrainingActivityAssignmentRepository.class),
                mock(GroupClassMemberRepository.class),
                mock(SmtpEmailService.class),
                mock(ThymeleafEmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                coordinator,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.createPending(new TrainingActivitySaveCommand("Strings", validInstructions(), false, "")))
                .isInstanceOf(InstructionQualityReviewException.class)
                .hasMessageContaining("La instrucción es demasiado vaga");
    }

    @Test
    void createPendingRejectsInvalidReviewEvenWhenConfirmedHashMatches() {
        var groupClassId = UUID.randomUUID();
        var activityRepository = mock(TrainingActivityRepository.class);

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                groupClassId,
                GroupClassMemberKind.PROFESSOR));

        var coordinator = mock(InstructionReviewCoordinator.class);
        when(coordinator.reviewBeforeSave(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(invalidDecision("review-hash-invalid", "TOO_GENERIC", "La instrucción no se puede usar como guía pedagógica."));

        var service = new TrainingActivityService(
                activityRepository,
                mock(TrainingActivityAssignmentRepository.class),
                mock(GroupClassMemberRepository.class),
                mock(SmtpEmailService.class),
                mock(ThymeleafEmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                coordinator,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.createPending(new TrainingActivitySaveCommand(
                "Strings",
                validInstructions(),
                false,
                "review-hash-invalid")))
                .isInstanceOf(InstructionQualityReviewException.class)
                .hasMessage("La instrucción no se puede usar como guía pedagógica.");
    }

    @Test
    void createPendingRejectsPromptInjectionEvenWhenConfirmedHashMatches() {
        var groupClassId = UUID.randomUUID();
        var activityRepository = mock(TrainingActivityRepository.class);

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                groupClassId,
                GroupClassMemberKind.PROFESSOR));

        var coordinator = mock(InstructionReviewCoordinator.class);
        when(coordinator.reviewBeforeSave(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(invalidDecision(
                        "review-hash-prompt-injection",
                        "PROMPT_INJECTION_ATTEMPT",
                        "El texto intenta cambiar reglas internas del tutor."));

        var service = new TrainingActivityService(
                activityRepository,
                mock(TrainingActivityAssignmentRepository.class),
                mock(GroupClassMemberRepository.class),
                mock(SmtpEmailService.class),
                mock(ThymeleafEmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                coordinator,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.createPending(new TrainingActivitySaveCommand(
                "Strings",
                validInstructions(),
                false,
                "review-hash-prompt-injection")))
                .isInstanceOf(InstructionQualityReviewException.class)
                .hasMessage("El texto intenta cambiar reglas internas del tutor.");
    }

    @Test
    void createPendingRejectsCachedNeedsImprovementReview() {
        var groupClassId = UUID.randomUUID();
        var activityRepository = mock(TrainingActivityRepository.class);

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                groupClassId,
                GroupClassMemberKind.PROFESSOR));

        var coordinator = mock(InstructionReviewCoordinator.class);
        when(coordinator.reviewBeforeSave(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(confirmableDecision("review-hash-cached-poor", InstructionQualityStatus.NEEDS_IMPROVEMENT, InstructionReviewStatus.COMPLETED_FROM_CACHE));

        var service = new TrainingActivityService(
                activityRepository,
                mock(TrainingActivityAssignmentRepository.class),
                mock(GroupClassMemberRepository.class),
                mock(SmtpEmailService.class),
                mock(ThymeleafEmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                coordinator,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.createPending(new TrainingActivitySaveCommand("Strings", validInstructions(), false, "")))
                .isInstanceOf(InstructionQualityReviewException.class)
                .hasMessageContaining("La instrucción es demasiado vaga");
    }

    @Test
    void createPendingStillBlocksNeedsImprovementEvenWhenConfirmedHashMatches() {
        var groupClassId = UUID.randomUUID();
        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.save(anyTrainingActivity())).thenAnswer(invocation -> invocation.getArgument(0));

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                groupClassId,
                GroupClassMemberKind.PROFESSOR));

        var coordinator = mock(InstructionReviewCoordinator.class);
        when(coordinator.reviewBeforeSave(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(confirmableDecision("review-hash-cached-poor", InstructionQualityStatus.NEEDS_IMPROVEMENT, InstructionReviewStatus.COMPLETED_FROM_CACHE));

        var service = new TrainingActivityService(
                activityRepository,
                mock(TrainingActivityAssignmentRepository.class),
                mock(GroupClassMemberRepository.class),
                mock(SmtpEmailService.class),
                mock(ThymeleafEmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                coordinator,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.createPending(new TrainingActivitySaveCommand(
                "Strings",
                validInstructions(),
                false,
                "review-hash-cached-poor")))
                .isInstanceOf(InstructionQualityReviewException.class)
                .hasMessageContaining("La instrucción es demasiado vaga");
        verify(activityRepository, never()).save(anyTrainingActivity());
    }

    @Test
    void updateRejectsPublishedActivities() {
        var activityId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();
        var activity = activity(activityId, groupClassId);

        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(
            new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                groupClassId,
                GroupClassMemberKind.PROFESSOR
            )
        );

        var service = new TrainingActivityService(
            activityRepository,
            mock(TrainingActivityAssignmentRepository.class),
            mock(GroupClassMemberRepository.class),
            mock(SmtpEmailService.class),
            mock(ThymeleafEmailTemplateService.class),
            applicationProperties(),
            contextResolver,
            new TrainingActivityLaunchedBus(),
            mock(SafeBrowserAssignmentStateBus.class),
            mock(InstructionReviewCoordinator.class),
            null
        );
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.update(activityId, "Nuevo título", "Nuevas instrucciones", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only draft training activities can be updated.");
    }

    @Test
    void listAllRejectsStudentContext() {
        var activityRepository = mock(TrainingActivityRepository.class);

        var service = new TrainingActivityService(
                activityRepository,
                mock(TrainingActivityAssignmentRepository.class),
                mock(GroupClassMemberRepository.class),
                mock(SmtpEmailService.class),
                mock(ThymeleafEmailTemplateService.class),
                applicationProperties(),
                studentContextResolver(),
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                mock(InstructionReviewCoordinator.class),
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(service::listAll)
                .isInstanceOf(SetupRequiredException.class)
                .hasMessageContaining("active professor class context");
        verify(activityRepository, never()).findByGroupClass_IdOrderByUpdatedAtDesc(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void professorOnlyOperationsRejectStudentContext() {
        var activityId = UUID.randomUUID();
        var activityRepository = mock(TrainingActivityRepository.class);
        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);

        var service = new TrainingActivityService(
                activityRepository,
                assignmentRepository,
                mock(GroupClassMemberRepository.class),
                mock(SmtpEmailService.class),
                mock(ThymeleafEmailTemplateService.class),
                applicationProperties(),
                studentContextResolver(),
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                mock(InstructionReviewCoordinator.class),
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.get(activityId))
                .isInstanceOf(SetupRequiredException.class)
                .hasMessageContaining("active professor class context");
        assertThatThrownBy(() -> service.listAssignments(activityId))
                .isInstanceOf(SetupRequiredException.class)
                .hasMessageContaining("active professor class context");
        assertThatThrownBy(() -> service.getInstructionReviewSnapshot(activityId))
                .isInstanceOf(SetupRequiredException.class)
                .hasMessageContaining("active professor class context");
        assertThatThrownBy(() -> service.update(activityId, "Nuevo título", validInstructions(), false))
                .isInstanceOf(SetupRequiredException.class)
                .hasMessageContaining("active professor class context");
        assertThatThrownBy(() -> service.delete(activityId))
                .isInstanceOf(SetupRequiredException.class)
                .hasMessageContaining("active professor class context");
        assertThatThrownBy(() -> service.launch(activityId))
                .isInstanceOf(SetupRequiredException.class)
                .hasMessageContaining("active professor class context");
        assertThatThrownBy(() -> service.close(activityId))
                .isInstanceOf(SetupRequiredException.class)
                .hasMessageContaining("active professor class context");

        verify(activityRepository, never()).findById(activityId);
        verify(assignmentRepository, never()).findByTrainingActivity_IdOrderByUpdatedAtDesc(activityId);
    }

    @Test
    void closeExpiresNonSubmittedAssignmentsAndLeavesSubmittedAssignmentsUntouched() {
        var activityId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();
        var activity = activity(activityId, groupClassId);
        var assigned = assignment(activity, TrainingActivityAssignmentStatus.ASSIGNED);
        var started = assignment(activity, TrainingActivityAssignmentStatus.STARTED);
        var submitted = assignment(activity, TrainingActivityAssignmentStatus.SUBMITTED);
        var excused = assignment(activity, TrainingActivityAssignmentStatus.EXCUSED);

        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(activityRepository.save(activity)).thenAnswer(invocation -> invocation.getArgument(0));

        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        when(
            assignmentRepository.findByTrainingActivity_IdAndStatusNot(
                activityId,
                TrainingActivityAssignmentStatus.SUBMITTED
            )
        ).thenReturn(List.of(assigned, started, excused));
        when(assignmentRepository.saveAll(List.of(assigned, started, excused))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(
            new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                groupClassId,
                GroupClassMemberKind.PROFESSOR
            )
        );

        var service = new TrainingActivityService(
            activityRepository,
            assignmentRepository,
            mock(GroupClassMemberRepository.class),
            mock(SmtpEmailService.class),
            mock(ThymeleafEmailTemplateService.class),
            applicationProperties(),
            contextResolver,
            new TrainingActivityLaunchedBus(),
            mock(SafeBrowserAssignmentStateBus.class),
            mock(InstructionReviewCoordinator.class),
            null
        );

        service.close(activityId);

        assertThat(ReflectionTestUtils.getField(activity, "status")).isEqualTo(
            TrainingActivityLifecycleStatus.CLOSED
        );
        assertThat(ReflectionTestUtils.getField(activity, "closesAt")).isNotNull();
        assertThat(ReflectionTestUtils.getField(assigned, "status")).isEqualTo(
            TrainingActivityAssignmentStatus.EXPIRED
        );
        assertThat(ReflectionTestUtils.getField(started, "status")).isEqualTo(
            TrainingActivityAssignmentStatus.EXPIRED
        );
        assertThat(ReflectionTestUtils.getField(submitted, "status")).isEqualTo(
            TrainingActivityAssignmentStatus.SUBMITTED
        );
        assertThat(ReflectionTestUtils.getField(excused, "status")).isEqualTo(
            TrainingActivityAssignmentStatus.EXCUSED
        );
    }

    @Test
    void closePublishesAssignmentStateNotificationsForAffectedAssignments() {
        var activityId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();
        var activity = activity(activityId, groupClassId);
        var assigned = assignment(activity, TrainingActivityAssignmentStatus.ASSIGNED);
        var started = assignment(activity, TrainingActivityAssignmentStatus.STARTED);
        var submitted = assignment(activity, TrainingActivityAssignmentStatus.SUBMITTED);
        var excused = assignment(activity, TrainingActivityAssignmentStatus.EXCUSED);

        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(activityRepository.save(activity)).thenAnswer(invocation -> invocation.getArgument(0));

        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        when(assignmentRepository.findByTrainingActivity_IdAndStatusNot(activityId, TrainingActivityAssignmentStatus.SUBMITTED))
                .thenReturn(List.of(assigned, started, excused));
        when(assignmentRepository.saveAll(List.of(assigned, started, excused))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.findByTrainingActivity_IdOrderByUpdatedAtDesc(activityId))
                .thenReturn(List.of(assigned, started, submitted, excused));

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(
            new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                groupClassId,
                GroupClassMemberKind.PROFESSOR
            )
        );
        var assignmentStateBus = mock(SafeBrowserAssignmentStateBus.class);

        var service = new TrainingActivityService(
            activityRepository,
            assignmentRepository,
            mock(GroupClassMemberRepository.class),
            mock(SmtpEmailService.class),
            mock(ThymeleafEmailTemplateService.class),
            applicationProperties(),
            contextResolver,
            new TrainingActivityLaunchedBus(),
            assignmentStateBus,
            mock(InstructionReviewCoordinator.class),
            null
        );

        service.close(activityId);

        var notificationCaptor = ArgumentCaptor.forClass(SafeBrowserAssignmentStateBus.Notification.class);
        verify(assignmentStateBus, org.mockito.Mockito.times(4)).publish(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .extracting(SafeBrowserAssignmentStateBus.Notification::trainingActivityId)
                .containsOnly(activityId);
        assertThat(notificationCaptor.getAllValues())
                .extracting(SafeBrowserAssignmentStateBus.Notification::activityClosed)
                .containsOnly(true);
        assertThat(notificationCaptor.getAllValues())
                .extracting(SafeBrowserAssignmentStateBus.Notification::assignmentId)
                .containsExactlyInAnyOrder(
                        field(assigned, "id"),
                        field(started, "id"),
                        field(submitted, "id"),
                        field(excused, "id"));
    }

    @Test
    void launchBlocksInvalidReview() {
        var activityId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();
        var activity = draftActivity(activityId, groupClassId);
        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(activityRepository.findFirstByCreatedByTenantAccount_IdAndStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                groupClassId,
                GroupClassMemberKind.PROFESSOR));

        var coordinator = mock(InstructionReviewCoordinator.class);
        var decision = invalidDecision("launch-invalid-hash", "TOO_GENERIC", "La instrucción no se puede usar como guía pedagógica.");
        when(coordinator.reviewBeforeSave(
                activity,
                field(activity, "title"),
                field(activity, "instructions"))).thenReturn(decision);
        stubApplyPersistedReview(coordinator, activity);

        var service = new TrainingActivityService(
                activityRepository,
                assignmentRepository,
                mock(GroupClassMemberRepository.class),
                mock(SmtpEmailService.class),
                mock(ThymeleafEmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                coordinator,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.launch(activityId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La instrucción no se puede usar como guía pedagógica.");
        verify(assignmentRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void launchBlocksUnavailableReview() {
        var activityId = UUID.randomUUID();
        var groupClassId = UUID.randomUUID();
        var activity = draftActivity(activityId, groupClassId);
        var activityRepository = mock(TrainingActivityRepository.class);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(activityRepository.findFirstByCreatedByTenantAccount_IdAndStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        when(activityRepository.save(activity)).thenAnswer(invocation -> invocation.getArgument(0));

        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        when(assignmentRepository.saveAll(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        var groupClassMemberRepository = mock(GroupClassMemberRepository.class);
        when(groupClassMemberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(groupClassId)).thenReturn(List.of(studentMember()));

        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                groupClassId,
                GroupClassMemberKind.PROFESSOR));

        var coordinator = mock(InstructionReviewCoordinator.class);
        var reviewResult = technicalUnavailableReview("launch-unavailable-hash");
        var unavailableException = new com.wornux.services.training_activity.instruction_review.InstructionReviewUnavailableException(
                reviewResult.summary(),
                reviewResult,
                null);
        when(coordinator.reviewBeforeSave(
                activity,
                field(activity, "title"),
                field(activity, "instructions"))).thenThrow(unavailableException);
        when(coordinator.unavailableSnapshot(activity, reviewResult)).thenReturn(unavailableAdvisoryDecision("launch-unavailable-hash").snapshot());
        stubApplyPersistedReview(coordinator, activity);

        var service = new TrainingActivityService(
                activityRepository,
                assignmentRepository,
                groupClassMemberRepository,
                mock(SmtpEmailService.class),
                mock(ThymeleafEmailTemplateService.class),
                applicationProperties(),
                contextResolver,
                new TrainingActivityLaunchedBus(),
                mock(SafeBrowserAssignmentStateBus.class),
                coordinator,
                null);
        ReflectionTestUtils.setField(service, "self", service);

        assertThatThrownBy(() -> service.launch(activityId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No pudimos completar la revisión automática");
    }

    private static TrainingActivity activity(UUID activityId, UUID groupClassId) {
        var groupClass = new GroupClass();
        ReflectionTestUtils.setField(groupClass, "id", groupClassId);
        var tenantAccount = new com.wornux.data.entities.identity.TenantAccount();
        ReflectionTestUtils.setField(tenantAccount, "id", UUID.randomUUID());

        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", activityId);
        ReflectionTestUtils.setField(activity, "groupClass", groupClass);
        ReflectionTestUtils.setField(activity, "createdByTenantAccount", tenantAccount);
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.PUBLISHED);
        ReflectionTestUtils.setField(activity, "updatedAt", Instant.now());
        return activity;
    }

    private static TrainingActivity draftActivity(UUID activityId, UUID groupClassId) {
        var activity = activity(activityId, groupClassId);
        ReflectionTestUtils.setField(activity, "status", TrainingActivityLifecycleStatus.DRAFT);
        return activity;
    }

    private static TrainingActivityAssignment assignment(TrainingActivity activity, TrainingActivityAssignmentStatus status) {
        var member = new com.wornux.data.entities.academic.GroupClassMember();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "groupClassMember", member);
        ReflectionTestUtils.setField(assignment, "status", status);
        ReflectionTestUtils.setField(assignment, "safeBrowserSessionActive", true);
        ReflectionTestUtils.setField(assignment, "updatedAt", Instant.now());
        return assignment;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) {
        return (T) ReflectionTestUtils.getField(target, name);
    }

    private static TrainingActivity anyTrainingActivity() {
        return org.mockito.ArgumentMatchers.any(TrainingActivity.class);
    }

    private static InstructionReviewCoordinator.ReviewBeforeSaveDecision unavailableAdvisoryDecision(String reviewHash) {
        var review = technicalUnavailableReview(reviewHash);
        return new InstructionReviewCoordinator.ReviewBeforeSaveDecision(
                new InstructionReviewSnapshotDto(
                        null,
                        reviewHash,
                        InstructionReviewStatus.UNAVAILABLE,
                        null,
                        false,
                        review.summary(),
                        true,
                        false,
                        List.of(),
                        "",
                        review.reviewedAt()),
                review,
                false);
    }

    private static InstructionReviewCoordinator.ReviewBeforeSaveDecision confirmableDecision(
            String reviewHash,
            InstructionQualityStatus qualityStatus,
            InstructionReviewStatus reviewStatus) {
        var validInstruction = qualityStatus == InstructionQualityStatus.GOOD
                || qualityStatus == InstructionQualityStatus.NEEDS_IMPROVEMENT;
        var message = qualityStatus == InstructionQualityStatus.NEEDS_IMPROVEMENT
                ? "La instrucción es demasiado vaga para guiar al tutor."
                : "La instrucción es usable, pero conviene precisar la evidencia esperada.";
        var canSave = qualityStatus == InstructionQualityStatus.GOOD;
        return new InstructionReviewCoordinator.ReviewBeforeSaveDecision(
                new InstructionReviewSnapshotDto(
                        null,
                        reviewHash,
                        reviewStatus,
                        qualityStatus,
                        canSave,
                        message,
                        false,
                        reviewStatus == InstructionReviewStatus.COMPLETED_FROM_CACHE,
                        List.of(new InstructionLintIssueDto(
                                "issue-1",
                                qualityStatus == InstructionQualityStatus.NEEDS_IMPROVEMENT ? "MISSING_EXPECTED_EVIDENCE" : "OPTIONAL_REFINEMENT",
                                "WARNING",
                                0,
                                10,
                                message,
                                "",
                                "Pide explicación, ejemplo y justificación sobre strlen y strcmp.",
                                "")),
                        "",
                        Instant.now()),
                new InstructionReviewResult(
                        validInstruction,
                        qualityStatus,
                        canSave,
                        canSave,
                        message,
                        message,
                        List.of(),
                        "",
                        "",
                        reviewHash,
                        Instant.now(),
                        "test-model",
                        "uc-006-v9-compact-fast-review"),
                canSave);
    }

    private static InstructionReviewCoordinator.ReviewBeforeSaveDecision invalidDecision(
            String reviewHash,
            String issueCode,
            String message) {
        return new InstructionReviewCoordinator.ReviewBeforeSaveDecision(
                new InstructionReviewSnapshotDto(
                        null,
                        reviewHash,
                        InstructionReviewStatus.NEEDS_USER_FIX,
                        null,
                        false,
                        message,
                        true,
                        false,
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
                        Instant.now()),
                new InstructionReviewResult(
                        false,
                        null,
                        false,
                        false,
                        message,
                        message,
                        List.of(),
                        "",
                        "",
                        reviewHash,
                        Instant.now(),
                        "test-model",
                        "uc-006-v9-compact-fast-review"),
                true);
    }

    private static InstructionReviewCoordinator.ReviewBeforeSaveDecision needsUserFixDecision(String reviewHash) {
        return new InstructionReviewCoordinator.ReviewBeforeSaveDecision(
                new InstructionReviewSnapshotDto(
                        null,
                        reviewHash,
                        InstructionReviewStatus.NEEDS_USER_FIX,
                        InstructionQualityStatus.NEEDS_IMPROVEMENT,
                        false,
                        "La instrucción es demasiado vaga para guiar al tutor.",
                        true,
                        false,
                        List.of(new InstructionLintIssueDto(
                        "issue-1",
                                "MISSING_EXPECTED_EVIDENCE",
                                "WARNING",
                                0,
                                10,
                                "La instrucción es demasiado vaga para guiar al tutor.",
                                "",
                                "replacement",
                                "")),
                        "",
                        Instant.now()),
                new InstructionReviewResult(
                        false,
                        InstructionQualityStatus.NEEDS_IMPROVEMENT,
                        false,
                        false,
                        "La instrucción es demasiado vaga para guiar al tutor.",
                        "La instrucción es demasiado vaga para guiar al tutor.",
                        List.of(),
                        "",
                        "",
                        reviewHash,
                        Instant.now(),
                        "test-model",
                        "uc-006-v9-compact-fast-review"),
                false);
    }

    private static InstructionReviewResult technicalUnavailableReview(String reviewHash) {
        return new InstructionReviewResult(
                false,
                null,
                InstructionReviewExecutionStatus.MODEL_UNAVAILABLE,
                false,
                false,
                "No pudimos completar la revisión automática de instrucciones. Intenta guardar de nuevo.",
                "No pudimos completar la revisión automática de instrucciones. Intenta guardar de nuevo.",
                List.of(),
                "",
                "",
                reviewHash,
                Instant.now(),
                "test-model",
                "uc-006-v9-compact-fast-review");
    }

    private static void stubApplyPersistedReview(InstructionReviewCoordinator coordinator, TrainingActivity activity) {
        Mockito.doAnswer(invocation -> {
            var snapshot = invocation.getArgument(1, InstructionReviewSnapshotDto.class);
            ReflectionTestUtils.setField(activity, "instructionReviewStatus", snapshot.reviewStatus());
            ReflectionTestUtils.setField(activity, "instructionReviewQualityStatus", snapshot.qualityStatus());
            ReflectionTestUtils.setField(activity, "instructionReviewMessage", snapshot.message());
            ReflectionTestUtils.setField(activity, "instructionReviewHash", snapshot.reviewHash());
            return null;
        }).when(coordinator).applyPersistedReview(
                org.mockito.ArgumentMatchers.eq(activity),
                org.mockito.ArgumentMatchers.any(InstructionReviewSnapshotDto.class),
                org.mockito.ArgumentMatchers.any(InstructionReviewResult.class));
    }

    private static com.wornux.data.entities.academic.GroupClassMember studentMember() {
        var account = new com.wornux.data.entities.identity.Account();
        ReflectionTestUtils.setField(account, "email", "student@example.com");
        ReflectionTestUtils.setField(account, "firstName", "Student");
        ReflectionTestUtils.setField(account, "lastName", "Test");
        var tenantAccount = new com.wornux.data.entities.identity.TenantAccount();
        ReflectionTestUtils.setField(tenantAccount, "account", account);
        var member = new com.wornux.data.entities.academic.GroupClassMember();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(member, "memberKind", GroupClassMemberKind.STUDENT);
        ReflectionTestUtils.setField(member, "tenantAccount", tenantAccount);
        return member;
    }

    private static ActiveAcademicContextResolver studentContextResolver() {
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                GroupClassMemberKind.STUDENT));
        return contextResolver;
    }

    private static String validInstructions() {
        return "Evalúa strings en C con preguntas socráticas progresivas, pide evidencia concreta y justificación sobre strlen, strcmp y lectura segura.";
    }

    private static ApplicationProperties applicationProperties() {
        var properties = new ApplicationProperties();
        properties.getEmail().setInvitationBaseUrl("http://localhost:3321");
        return properties;
    }
}
