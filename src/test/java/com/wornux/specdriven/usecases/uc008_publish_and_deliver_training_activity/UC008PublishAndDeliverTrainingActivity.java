package com.wornux.specdriven.usecases.uc008_publish_and_deliver_training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.config.ApplicationProperties;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.OutboxEvent;
import com.wornux.data.entities.training_activity.OutboxRecipientDelivery;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.training_activity.OutboxEventRepository;
import com.wornux.data.repositories.training_activity.OutboxRecipientDeliveryRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewOverrideAction;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.data.repositories.training_activity.TrainingActivityAiJobRepository;
import com.wornux.data.repositories.training_activity.instruction_review.TrainingInstructionReviewOverrideRepository;
import com.wornux.data.repositories.training_activity.instruction_review.TrainingInstructionReviewRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.email.EmailService;
import com.wornux.services.email.EmailTemplateService;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.TrainingActivityLaunchedBus;
import com.wornux.services.training_activity.TrainingActivitySaveCommand;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.training_activity.instruction_review.AdvisoryInstructionReviewService;
import com.wornux.services.training_activity.instruction_review.InstructionReviewCoordinator;
import com.wornux.services.training_activity.instruction_review.InstructionReviewService;
import com.wornux.services.training_activity.instruction_review.InstructionReviewSnapshotDto;
import org.junit.jupiter.api.Test;

class UC008PublishAndDeliverTrainingActivity {

    @Test
    void af1_sensitiveActivityMutationsDeclareServiceLayerPermissions() throws Exception {
        assertThat(TrainingActivityService.class.getMethod("createPending", TrainingActivitySaveCommand.class)
                .getAnnotation(RequiresPermission.class).value()).isEqualTo(AppPermission.TRAINING_ACTIVITY_CREATE);
        assertThat(TrainingActivityService.class.getMethod("reviewDraft", TrainingActivitySaveCommand.class)
                .getAnnotation(RequiresPermission.class).value()).isEqualTo(AppPermission.TRAINING_ACTIVITY_CREATE);
        assertThat(TrainingActivityService.class.getMethod("update", UUID.class, TrainingActivitySaveCommand.class)
                .getAnnotation(RequiresPermission.class).value()).isEqualTo(AppPermission.TRAINING_ACTIVITY_UPDATE);
        assertThat(TrainingActivityService.class.getMethod("delete", UUID.class)
                .getAnnotation(RequiresPermission.class).value()).isEqualTo(AppPermission.TRAINING_ACTIVITY_DELETE);
        assertThat(TrainingActivityService.class.getMethod("launch", UUID.class, long.class, boolean.class)
                .getAnnotation(RequiresPermission.class).value()).isEqualTo(AppPermission.TRAINING_ACTIVITY_UPDATE);
        assertThat(TrainingActivityService.class.getMethod("close", UUID.class)
                .getAnnotation(RequiresPermission.class).value()).isEqualTo(AppPermission.TRAINING_ACTIVITY_UPDATE);
    }

    @Test
    void mainFlow_publishesAssignmentsAndOutboxAtomicallyBeforeNotificationWork() {
        var fixture = fixture();
        var activity = draftActivity();
        inContext(fixture, activity);
        var student = student(activity.getGroupClass());
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(fixture.memberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(activity.getGroupClass().getId()))
                .thenReturn(List.of(student));
        when(fixture.advisory.current(activity.getId(), activity.getInstructions())).thenReturn(goodReview(activity));

        var assigned = fixture.service.launch(activity.getId(), activity.getVersion(), false);

        assertThat(assigned).isEqualTo(1);
        assertThat(activity.getStatus()).isEqualTo(TrainingActivityLifecycleStatus.PUBLISHED);
        assertThat(activity.getPublishedAt()).isNotNull();
        verify(fixture.assignmentRepository).saveAll(anyList());
        verify(fixture.eventRepository).save(any(OutboxEvent.class));
        verify(fixture.deliveryRepository).saveAll(anyList());
        verify(fixture.emailService, never()).send(any());
    }

    @Test
    void af4_noEligibleStudentsLeavesDraftWithoutAssignmentsOrOutbox() {
        var fixture = fixture();
        var activity = draftActivity();
        inContext(fixture, activity);
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(fixture.memberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(activity.getGroupClass().getId()))
                .thenReturn(List.of());
        when(fixture.advisory.current(activity.getId(), activity.getInstructions())).thenReturn(goodReview(activity));

        assertThatThrownBy(() -> fixture.service.launch(activity.getId(), activity.getVersion(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no eligible students");

        assertThat(activity.getStatus()).isEqualTo(TrainingActivityLifecycleStatus.DRAFT);
        verify(fixture.assignmentRepository, never()).saveAll(anyList());
        verify(fixture.eventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void af6_repeatedCompletedPublicationReturnsTheExistingAssignmentCount() {
        var fixture = fixture();
        var activity = draftActivity();
        inContext(fixture, activity);
        activity.setStatus(TrainingActivityLifecycleStatus.PUBLISHED);
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(fixture.assignmentRepository.countByTrainingActivity_Id(activity.getId())).thenReturn(2L);

        assertThat(fixture.service.launch(activity.getId(), activity.getVersion(), false)).isEqualTo(2);
        verify(fixture.eventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void af3_publishAnywayRecordsAnOverrideForTheCurrentInstructionsBeforePublication() {
        var fixture = fixture();
        var activity = draftActivity();
        inContext(fixture, activity);
        var student = student(activity.getGroupClass());
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(fixture.memberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(activity.getGroupClass().getId()))
                .thenReturn(List.of(student));
        when(fixture.advisory.current(activity.getId(), activity.getInstructions())).thenReturn(neededReview(activity));

        fixture.service.launch(activity.getId(), activity.getVersion(), true);

        verify(fixture.advisory).recordOverride(activity, fixture.context.groupClassMemberId(),
                activity.getInstructions(), InstructionReviewOverrideAction.PUBLISH);
    }

    @Test
    void af1_stalePublicationVersionDoesNotCreateAssignmentsOrOutbox() {
        var fixture = fixture();
        var activity = draftActivity();
        inContext(fixture, activity);
        activity.setVersion(3);
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> fixture.service.launch(activity.getId(), 2, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed");

        verify(fixture.assignmentRepository, never()).saveAll(anyList());
        verify(fixture.eventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void af3_missingReviewRecordsAPublishOverrideForTheCurrentInstructionHash() {
        var reviewRepository = mock(TrainingInstructionReviewRepository.class);
        var overrideRepository = mock(TrainingInstructionReviewOverrideRepository.class);
        var reviewEngine = mock(InstructionReviewService.class);
        when(reviewEngine.hashNormalizedInstructions(any())).thenReturn("current-instruction-hash");
        when(reviewEngine.currentModelName()).thenReturn("review-model");
        when(reviewEngine.promptVersion()).thenReturn("rubric-v1");
        when(reviewRepository.findFirstByTrainingActivity_IdAndInstructionsHashAndModelNameAndRubricVersionOrderByRequestedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        var activity = draftActivity();

        new AdvisoryInstructionReviewService(reviewRepository, overrideRepository,
                mock(TrainingActivityAiJobRepository.class), reviewEngine)
                .recordOverride(activity, UUID.randomUUID(), activity.getInstructions(), InstructionReviewOverrideAction.PUBLISH);

        verify(overrideRepository).save(any());
    }

    private static Fixture fixture() {
        var activityRepository = mock(TrainingActivityRepository.class);
        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        var memberRepository = mock(GroupClassMemberRepository.class);
        var eventRepository = mock(OutboxEventRepository.class);
        var deliveryRepository = mock(OutboxRecipientDeliveryRepository.class);
        var advisory = mock(AdvisoryInstructionReviewService.class);
        var context = new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                GroupClassMemberKind.PROFESSOR);
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(context);
        when(activityRepository.save(any(TrainingActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        var emailService = mock(EmailService.class);
        var service = new TrainingActivityService(activityRepository, assignmentRepository, eventRepository, deliveryRepository,
                memberRepository, emailService, mock(EmailTemplateService.class), applicationProperties(), contextResolver,
                new TrainingActivityLaunchedBus(), mock(SafeBrowserAssignmentStateBus.class), mock(InstructionReviewCoordinator.class),
                advisory, null);
        return new Fixture(service, activityRepository, assignmentRepository, memberRepository, eventRepository, deliveryRepository,
                advisory, context, emailService);
    }

    private static ApplicationProperties applicationProperties() {
        var properties = new ApplicationProperties();
        properties.getEmail().setInvitationBaseUrl("http://localhost:3321");
        return properties;
    }

    private static TrainingActivity draftActivity() {
        var activity = new TrainingActivity();
        activity.setId(UUID.randomUUID());
        var groupClass = new GroupClass();
        groupClass.setId(UUID.randomUUID());
        activity.setGroupClass(groupClass);
        var tenantAccount = new TenantAccount();
        tenantAccount.setId(UUID.randomUUID());
        activity.setCreatedByTenantAccount(tenantAccount);
        activity.setTitle("Pointers");
        activity.setInstructions("Explain pointer ownership and use one concrete example before reaching a conclusion.");
        activity.setStatus(TrainingActivityLifecycleStatus.DRAFT);
        activity.setCreatedAt(Instant.now());
        activity.setUpdatedAt(Instant.now());
        return activity;
    }

    private static void inContext(Fixture fixture, TrainingActivity activity) {
        activity.getGroupClass().setId(fixture.context.groupClassId());
    }

    private static GroupClassMember student(GroupClass groupClass) {
        var student = new GroupClassMember();
        student.setId(UUID.randomUUID());
        student.setGroupClass(groupClass);
        student.setMemberKind(GroupClassMemberKind.STUDENT);
        return student;
    }

    private static InstructionReviewSnapshotDto goodReview(TrainingActivity activity) {
        return new InstructionReviewSnapshotDto(activity.getId(), "current-hash", InstructionReviewStatus.COMPLETED,
                InstructionQualityStatus.GOOD, true, "Ready", false, false, List.of(), "", Instant.now());
    }

    private static InstructionReviewSnapshotDto neededReview(TrainingActivity activity) {
        return new InstructionReviewSnapshotDto(activity.getId(), "current-hash", InstructionReviewStatus.COMPLETED,
                InstructionQualityStatus.NEEDS_IMPROVEMENT, false, "Needs work", false, false, List.of(), "", Instant.now());
    }

    private record Fixture(
            TrainingActivityService service,
            TrainingActivityRepository activityRepository,
            TrainingActivityAssignmentRepository assignmentRepository,
            GroupClassMemberRepository memberRepository,
            OutboxEventRepository eventRepository,
            OutboxRecipientDeliveryRepository deliveryRepository,
            AdvisoryInstructionReviewService advisory,
            ActiveAcademicContext context,
            EmailService emailService) {
    }
}
