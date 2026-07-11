package com.wornux.specdriven.usecases.uc008_publish_and_deliver_training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.training_activity.OutboxEvent;
import com.wornux.data.entities.training_activity.OutboxEventStatus;
import com.wornux.data.entities.training_activity.OutboxRecipientDelivery;
import com.wornux.data.entities.training_activity.OutboxRecipientDeliveryStatus;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.repositories.training_activity.OutboxEventRepository;
import com.wornux.data.repositories.training_activity.OutboxRecipientDeliveryRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.training_activity.ActivityPublicationDeliveryRecoveryService;
import com.wornux.services.training_activity.ActivityPublicationNotificationMetrics;
import com.wornux.services.training_activity.ActivityPublicationNotificationService;
import com.wornux.services.training_activity.ActivityPublicationNotificationWorker;
import com.wornux.services.training_activity.RecipientNotificationTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ActivityPublicationDeliveryRecoveryServiceTest {

    @Test
    void af9_failedParentAndUncertainDeliveryBecomeClaimableForManualReplay() {
        var fixture = fixture(OutboxEventStatus.FAILED, OutboxRecipientDeliveryStatus.UNCERTAIN);

        fixture.service().replay(fixture.delivery().getId());

        assertThat(fixture.delivery().getStatus()).isEqualTo(OutboxRecipientDeliveryStatus.PENDING);
        assertThat(fixture.delivery().getAttemptCount()).isZero();
        assertThat(fixture.delivery().getLeaseUntil()).isNull();
        assertThat(fixture.delivery().getAvailableAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(fixture.delivery().getLastErrorCode()).isEqualTo("MANUAL_REPLAY_REQUESTED");
        assertThat(fixture.event().getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(fixture.event().getLeaseUntil()).isNull();
        assertThat(fixture.event().getAvailableAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(fixture.event().getLastErrorCode()).isEqualTo("MANUAL_REPLAY_REQUESTED");
        verify(fixture.authorizationService()).check(AppPermission.TRAINING_ACTIVITY_UPDATE);
    }

    @Test
    void af9_workerProcessesAReplayedDeliveryWithoutResendingCompletedRecipients() {
        var fixture = fixture(OutboxEventStatus.FAILED, OutboxRecipientDeliveryStatus.FAILED);
        var transport = mock(RecipientNotificationTransport.class);
        when(fixture.eventRepository().findByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(fixture.event()));
        when(fixture.eventRepository().findByStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(fixture.deliveryRepository().findByOutboxEvent_IdAndStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(fixture.delivery()));
        when(fixture.deliveryRepository().findByOutboxEvent_IdAndStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(transport.deliver(org.mockito.ArgumentMatchers.any())).thenReturn(RecipientNotificationTransport.DeliveryOutcome.ACCEPTED);

        fixture.service().replay(fixture.delivery().getId());
        new ActivityPublicationNotificationWorker(
                new ActivityPublicationNotificationService(fixture.eventRepository(), fixture.deliveryRepository(), transport),
                new ActivityPublicationNotificationMetrics(new SimpleMeterRegistry())).poll();

        assertThat(fixture.delivery().getStatus()).isEqualTo(OutboxRecipientDeliveryStatus.SENT);
        assertThat(fixture.event().getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        verify(transport).deliver(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void af9_repeatedReplayIsIdempotentAndDoesNotRegressAProcessingParent() {
        var fixture = fixture(OutboxEventStatus.PROCESSING, OutboxRecipientDeliveryStatus.PENDING);
        var leaseUntil = Instant.now().plusSeconds(60);
        fixture.event().setLeaseUntil(leaseUntil);

        fixture.service().replay(fixture.delivery().getId());
        fixture.service().replay(fixture.delivery().getId());

        assertThat(fixture.delivery().getStatus()).isEqualTo(OutboxRecipientDeliveryStatus.PENDING);
        assertThat(fixture.event().getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(fixture.event().getLeaseUntil()).isEqualTo(leaseUntil);
    }

    @Test
    void af9_completedRecipientAndPublishedParentAreNeverReplayed() {
        var fixture = fixture(OutboxEventStatus.PUBLISHED, OutboxRecipientDeliveryStatus.SENT);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service().replay(fixture.delivery().getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(fixture.delivery().getStatus()).isEqualTo(OutboxRecipientDeliveryStatus.SENT);
        assertThat(fixture.event().getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void af9_parentLockFailureLeavesTheDeliveryUnchanged() {
        var fixture = fixture(OutboxEventStatus.FAILED, OutboxRecipientDeliveryStatus.UNCERTAIN);
        when(fixture.eventRepository().findLockedById(fixture.event().getId())).thenThrow(new RuntimeException("lock failure"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service().replay(fixture.delivery().getId()))
                .isInstanceOf(RuntimeException.class);

        assertThat(fixture.delivery().getStatus()).isEqualTo(OutboxRecipientDeliveryStatus.UNCERTAIN);
        assertThat(fixture.event().getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }

    private static Fixture fixture(OutboxEventStatus eventStatus, OutboxRecipientDeliveryStatus deliveryStatus) {
        var eventRepository = mock(OutboxEventRepository.class);
        var deliveryRepository = mock(OutboxRecipientDeliveryRepository.class);
        var activityRepository = mock(TrainingActivityRepository.class);
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        var authorizationService = mock(AuthorizationService.class);
        var groupClassId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        var delivery = new OutboxRecipientDelivery();
        delivery.setId(UUID.randomUUID());
        delivery.setStatus(deliveryStatus);
        var event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateId(activityId);
        event.setStatus(eventStatus);
        event.setAvailableAt(Instant.now());
        delivery.setOutboxEvent(event);
        var account = new Account();
        account.setEmail("student@example.test");
        var tenantAccount = new TenantAccount();
        tenantAccount.setAccount(account);
        var member = new GroupClassMember();
        member.setTenantAccount(tenantAccount);
        delivery.setGroupClassMember(member);
        var activity = new TrainingActivity();
        var groupClass = new GroupClass();
        groupClass.setId(groupClassId);
        activity.setId(activityId);
        activity.setGroupClass(groupClass);
        when(deliveryRepository.findById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(eventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));
        when(deliveryRepository.findLockedById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                groupClassId, GroupClassMemberKind.PROFESSOR));

        return new Fixture(eventRepository, deliveryRepository, authorizationService, event, delivery,
                new ActivityPublicationDeliveryRecoveryService(eventRepository, deliveryRepository, activityRepository, contextResolver,
                        authorizationService));
    }

    private record Fixture(
            OutboxEventRepository eventRepository,
            OutboxRecipientDeliveryRepository deliveryRepository,
            AuthorizationService authorizationService,
            OutboxEvent event,
            OutboxRecipientDelivery delivery,
            ActivityPublicationDeliveryRecoveryService service) {
    }
}
