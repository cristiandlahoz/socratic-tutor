package com.wornux.specdriven.usecases.uc008_publish_and_deliver_training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.training_activity.OutboxEvent;
import com.wornux.data.entities.training_activity.OutboxEventStatus;
import com.wornux.data.entities.training_activity.OutboxRecipientDelivery;
import com.wornux.data.entities.training_activity.OutboxRecipientDeliveryStatus;
import com.wornux.data.repositories.training_activity.OutboxEventRepository;
import com.wornux.data.repositories.training_activity.OutboxRecipientDeliveryRepository;
import com.wornux.services.training_activity.ActivityPublicationNotificationService;
import com.wornux.services.training_activity.ActivityPublicationNotificationWorker;
import com.wornux.services.training_activity.ActivityPublicationNotificationMetrics;
import com.wornux.services.training_activity.RecipientNotificationTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ActivityPublicationNotificationWorkerTest {

    @Test
    void af8_uncertainSmtpOutcomeIsAuditedAndNeverAutomaticallyRetried() {
        var eventRepository = mock(OutboxEventRepository.class);
        var deliveryRepository = mock(OutboxRecipientDeliveryRepository.class);
        var transport = mock(RecipientNotificationTransport.class);
        var event = event();
        var delivery = delivery(event, OutboxRecipientDeliveryStatus.PENDING);
        when(eventRepository.findByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of(event));
        when(eventRepository.findByStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(any(), any(), any())).thenReturn(List.of());
        when(eventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));
        when(deliveryRepository.findByOutboxEvent_IdAndStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(any(), any(), any(), any()))
                .thenReturn(List.of(delivery));
        when(deliveryRepository.findByOutboxEvent_IdAndStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
                any(), any(), any(), any())).thenReturn(List.of());
        when(deliveryRepository.findLockedById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(deliveryRepository.countByOutboxEvent_IdAndStatusNot(event.getId(), OutboxRecipientDeliveryStatus.SENT)).thenReturn(1L);
        when(deliveryRepository.countByOutboxEvent_IdAndStatus(event.getId(), OutboxRecipientDeliveryStatus.PENDING)).thenReturn(0L);
        when(deliveryRepository.countByOutboxEvent_IdAndStatus(event.getId(), OutboxRecipientDeliveryStatus.PROCESSING)).thenReturn(0L);
        when(transport.deliver(any())).thenReturn(RecipientNotificationTransport.DeliveryOutcome.UNCERTAIN_AFTER_SEND);

        var meterRegistry = new SimpleMeterRegistry();
        new ActivityPublicationNotificationWorker(
                new ActivityPublicationNotificationService(eventRepository, deliveryRepository, transport),
                new ActivityPublicationNotificationMetrics(meterRegistry)).poll();

        assertThat(delivery.getStatus()).isEqualTo(OutboxRecipientDeliveryStatus.UNCERTAIN);
        assertThat(delivery.getLastErrorCode()).isEqualTo("SMTP_ACCEPTANCE_UNCERTAIN");
        verify(transport).deliver(any());
    }

    @Test
    void af8_transportExceptionAfterSendingBoundaryBecomesUncertainAndIsNotRetried() {
        var eventRepository = mock(OutboxEventRepository.class);
        var deliveryRepository = mock(OutboxRecipientDeliveryRepository.class);
        var transport = mock(RecipientNotificationTransport.class);
        var event = event();
        var delivery = delivery(event, OutboxRecipientDeliveryStatus.PENDING);
        when(eventRepository.findByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of(event));
        when(eventRepository.findByStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(any(), any(), any())).thenReturn(List.of());
        when(eventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));
        when(deliveryRepository.findByOutboxEvent_IdAndStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(any(), any(), any(), any()))
                .thenReturn(List.of(delivery));
        when(deliveryRepository.findByOutboxEvent_IdAndStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
                any(), any(), any(), any())).thenReturn(List.of());
        when(deliveryRepository.findLockedById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(deliveryRepository.countByOutboxEvent_IdAndStatusNot(event.getId(), OutboxRecipientDeliveryStatus.SENT)).thenReturn(1L);
        when(deliveryRepository.countByOutboxEvent_IdAndStatus(event.getId(), OutboxRecipientDeliveryStatus.PENDING)).thenReturn(0L);
        when(deliveryRepository.countByOutboxEvent_IdAndStatus(event.getId(), OutboxRecipientDeliveryStatus.PROCESSING)).thenReturn(0L);
        when(transport.deliver(any())).thenThrow(new RuntimeException("transport disconnected"));
        var meterRegistry = new SimpleMeterRegistry();
        var worker = new ActivityPublicationNotificationWorker(
                new ActivityPublicationNotificationService(eventRepository, deliveryRepository, transport),
                new ActivityPublicationNotificationMetrics(meterRegistry));

        worker.poll();
        worker.poll();

        assertThat(delivery.getStatus()).isEqualTo(OutboxRecipientDeliveryStatus.UNCERTAIN);
        assertThat(delivery.getLastErrorCode()).isEqualTo("SMTP_ACCEPTANCE_UNCERTAIN");
        assertThat(meterRegistry.get(ActivityPublicationNotificationMetrics.POLLS).counter().count()).isEqualTo(2);
        assertThat(meterRegistry.get(ActivityPublicationNotificationMetrics.DELIVERY_FAILURE).counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get(ActivityPublicationNotificationMetrics.PROCESSING_DURATION).timer().count()).isEqualTo(2);
        verify(transport, times(1)).deliver(any());
    }

    @Test
    void resilience_expiredRecoveryQueriesAreBoundedAndScopedToTheEvent() {
        var eventRepository = mock(OutboxEventRepository.class);
        var deliveryRepository = mock(OutboxRecipientDeliveryRepository.class);
        var event = event();
        var delivery = delivery(event, OutboxRecipientDeliveryStatus.PROCESSING);
        when(eventRepository.findByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(eventRepository.findByStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of(event));
        when(deliveryRepository.findByOutboxEvent_IdAndStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(deliveryRepository.findByOutboxEvent_IdAndStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
                eq(event.getId()), any(), any(), any())).thenReturn(List.of(delivery));
        var service = new ActivityPublicationNotificationService(eventRepository, deliveryRepository, mock(RecipientNotificationTransport.class));

        assertThat(service.availableEventIds(Instant.now())).containsExactly(event.getId());
        assertThat(service.availableDeliveryIds(event.getId(), Instant.now())).containsExactly(delivery.getId());

        verify(eventRepository).findByStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PROCESSING), any(), any());
        verify(deliveryRepository, times(2)).findByOutboxEvent_IdAndStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
                eq(event.getId()), any(), any(), any());
    }

    @Test
    void resilience_expiredSendingLeaseBecomesUncertainWithoutCallingTransport() {
        var eventRepository = mock(OutboxEventRepository.class);
        var deliveryRepository = mock(OutboxRecipientDeliveryRepository.class);
        var transport = mock(RecipientNotificationTransport.class);
        var event = event();
        var delivery = delivery(event, OutboxRecipientDeliveryStatus.SENDING);
        delivery.setLeaseUntil(Instant.now().minusSeconds(1));
        when(deliveryRepository.findLockedById(delivery.getId())).thenReturn(Optional.of(delivery));

        var result = new ActivityPublicationNotificationService(eventRepository, deliveryRepository, transport)
                .claimDelivery(delivery.getId(), Instant.now());

        assertThat(result).isNull();
        assertThat(delivery.getStatus()).isEqualTo(OutboxRecipientDeliveryStatus.UNCERTAIN);
        assertThat(delivery.getLastErrorCode()).isEqualTo("LEASE_EXPIRED_AFTER_SEND_BOUNDARY");
    }

    @Test
    void resilience_pollFailureIsMeasuredAndDoesNotEscapeTheScheduler() {
        var eventRepository = mock(OutboxEventRepository.class);
        var deliveryRepository = mock(OutboxRecipientDeliveryRepository.class);
        when(eventRepository.findByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(any(), any(), any()))
                .thenThrow(new RuntimeException("database unavailable"));
        var meterRegistry = new SimpleMeterRegistry();
        var worker = new ActivityPublicationNotificationWorker(
                new ActivityPublicationNotificationService(eventRepository, deliveryRepository, mock(RecipientNotificationTransport.class)),
                new ActivityPublicationNotificationMetrics(meterRegistry));

        worker.poll();

        assertThat(meterRegistry.get(ActivityPublicationNotificationMetrics.POLLS).counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get(ActivityPublicationNotificationMetrics.POLL_FAILURES).counter().count()).isEqualTo(1);
    }

    private static OutboxEvent event() {
        var event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setStatus(OutboxEventStatus.PENDING);
        event.setAvailableAt(Instant.now());
        event.setCreatedAt(Instant.now());
        return event;
    }

    private static OutboxRecipientDelivery delivery(OutboxEvent event, OutboxRecipientDeliveryStatus status) {
        var account = new Account();
        account.setEmail("student@example.test");
        var tenantAccount = new TenantAccount();
        tenantAccount.setAccount(account);
        var member = new GroupClassMember();
        member.setTenantAccount(tenantAccount);
        var delivery = new OutboxRecipientDelivery();
        delivery.setId(UUID.randomUUID());
        delivery.setOutboxEvent(event);
        delivery.setGroupClassMember(member);
        delivery.setStatus(status);
        delivery.setAvailableAt(Instant.now());
        delivery.setCreatedAt(Instant.now());
        delivery.setIdempotencyKey("delivery:" + delivery.getId());
        return delivery;
    }
}
