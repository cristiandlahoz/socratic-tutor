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
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.TrainingActivityLaunchedBus;
import com.wornux.services.training_activity.TrainingActivitySaveCommand;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.training_activity.instruction_review.AdvisoryInstructionReviewService;
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
        assertThat(TrainingActivityService.class.getMethod("launch", UUID.class, long.class)
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

        var assigned = fixture.service.launch(activity.getId(), activity.getVersion());

        assertThat(assigned).isEqualTo(1);
        assertThat(activity.getStatus()).isEqualTo(TrainingActivityLifecycleStatus.PUBLISHED);
        assertThat(activity.getPublishedAt()).isNotNull();
        verify(fixture.assignmentRepository).saveAll(anyList());
        verify(fixture.eventRepository).save(any(OutboxEvent.class));
        verify(fixture.deliveryRepository).saveAll(anyList());
    }

    @Test
    void af4_noEligibleStudentsLeavesDraftWithoutAssignmentsOrOutbox() {
        var fixture = fixture();
        var activity = draftActivity();
        inContext(fixture, activity);
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(fixture.memberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(activity.getGroupClass().getId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> fixture.service.launch(activity.getId(), activity.getVersion()))
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

        assertThat(fixture.service.launch(activity.getId(), activity.getVersion())).isEqualTo(2);
        verify(fixture.eventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void af1_stalePublicationVersionDoesNotCreateAssignmentsOrOutbox() {
        var fixture = fixture();
        var activity = draftActivity();
        inContext(fixture, activity);
        activity.setVersion(3);
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> fixture.service.launch(activity.getId(), 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cambió");

        verify(fixture.assignmentRepository, never()).saveAll(anyList());
        verify(fixture.eventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void af2_secondActiveActivityForTheSameProfessorIsRejectedBeforeAnySideEffects() {
        var fixture = fixture();
        var activity = draftActivity();
        inContext(fixture, activity);
        var activeActivity = draftActivity();
        activeActivity.setStatus(TrainingActivityLifecycleStatus.PUBLISHED);
        activeActivity.getCreatedByTenantAccount().setId(fixture.context.tenantAccountId());
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(fixture.activityRepository.findFirstByCreatedByTenantAccount_IdAndStatus(
                fixture.context.tenantAccountId(), TrainingActivityLifecycleStatus.PUBLISHED)).thenReturn(Optional.of(activeActivity));

        assertThatThrownBy(() -> fixture.service.launch(activity.getId(), activity.getVersion()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Ya tienes una actividad en ejecución. Ciérrala antes de publicar otra.");

        assertThat(activity.getStatus()).isEqualTo(TrainingActivityLifecycleStatus.DRAFT);
        verify(fixture.assignmentRepository, never()).saveAll(anyList());
        verify(fixture.eventRepository, never()).save(any(OutboxEvent.class));
        verify(fixture.deliveryRepository, never()).saveAll(anyList());
    }

    @Test
    void af2_closedActivitiesDoNotBlockAnotherLaunchForTheSameProfessor() {
        var fixture = fixture();
        var activity = draftActivity();
        inContext(fixture, activity);
        var student = student(activity.getGroupClass());
        when(fixture.activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(fixture.activityRepository.findFirstByCreatedByTenantAccount_IdAndStatus(
                fixture.context.tenantAccountId(), TrainingActivityLifecycleStatus.PUBLISHED)).thenReturn(Optional.empty());
        when(fixture.memberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(activity.getGroupClass().getId()))
                .thenReturn(List.of(student));

        assertThat(fixture.service.launch(activity.getId(), activity.getVersion())).isEqualTo(1);
        assertThat(activity.getStatus()).isEqualTo(TrainingActivityLifecycleStatus.PUBLISHED);
    }

    @Test
    void af2_ownerCanCloseAnActivityAndPublishTheNextOne() {
        var fixture = fixture();
        var activeActivity = draftActivity();
        inContext(fixture, activeActivity);
        activeActivity.setStatus(TrainingActivityLifecycleStatus.PUBLISHED);
        var nextActivity = draftActivity();
        inContext(fixture, nextActivity);
        var student = student(nextActivity.getGroupClass());
        when(fixture.activityRepository.findById(activeActivity.getId())).thenReturn(Optional.of(activeActivity));
        when(fixture.assignmentRepository.findByTrainingActivity_IdAndStatusNot(
                activeActivity.getId(), com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus.SUBMITTED))
                .thenReturn(List.of());
        when(fixture.assignmentRepository.findByTrainingActivity_IdOrderByUpdatedAtDesc(activeActivity.getId())).thenReturn(List.of());

        fixture.service.close(activeActivity.getId());

        assertThat(activeActivity.getStatus()).isEqualTo(TrainingActivityLifecycleStatus.CLOSED);
        when(fixture.activityRepository.findById(nextActivity.getId())).thenReturn(Optional.of(nextActivity));
        when(fixture.activityRepository.findFirstByCreatedByTenantAccount_IdAndStatus(
                fixture.context.tenantAccountId(), TrainingActivityLifecycleStatus.PUBLISHED)).thenReturn(Optional.empty());
        when(fixture.memberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(nextActivity.getGroupClass().getId()))
                .thenReturn(List.of(student));

        assertThat(fixture.service.launch(nextActivity.getId(), nextActivity.getVersion())).isEqualTo(1);
        assertThat(nextActivity.getStatus()).isEqualTo(TrainingActivityLifecycleStatus.PUBLISHED);
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
        var service = new TrainingActivityService(activityRepository, assignmentRepository, eventRepository, deliveryRepository,
                memberRepository, contextResolver,
                new TrainingActivityLaunchedBus(), mock(SafeBrowserAssignmentStateBus.class), advisory, null);
        return new Fixture(service, activityRepository, assignmentRepository, memberRepository, eventRepository, deliveryRepository,
                advisory, context);
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
        activity.getCreatedByTenantAccount().setId(fixture.context.tenantAccountId());
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
                InstructionQualityStatus.GOOD, true, "Ready", false, List.of(), "", Instant.now());
    }

    private static InstructionReviewSnapshotDto neededReview(TrainingActivity activity) {
        return new InstructionReviewSnapshotDto(activity.getId(), "current-hash", InstructionReviewStatus.COMPLETED,
                InstructionQualityStatus.NEEDS_IMPROVEMENT, false, "Needs work", false, List.of(), "", Instant.now());
    }

    private record Fixture(
            TrainingActivityService service,
            TrainingActivityRepository activityRepository,
            TrainingActivityAssignmentRepository assignmentRepository,
            GroupClassMemberRepository memberRepository,
            OutboxEventRepository eventRepository,
            OutboxRecipientDeliveryRepository deliveryRepository,
            AdvisoryInstructionReviewService advisory,
            ActiveAcademicContext context) {
    }
}
