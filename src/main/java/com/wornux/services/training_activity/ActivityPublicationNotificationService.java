package com.wornux.services.training_activity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.training_activity.OutboxEventStatus;
import com.wornux.data.entities.training_activity.OutboxRecipientDelivery;
import com.wornux.data.entities.training_activity.OutboxRecipientDeliveryStatus;
import com.wornux.data.repositories.training_activity.OutboxEventRepository;
import com.wornux.data.repositories.training_activity.OutboxRecipientDeliveryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityPublicationNotificationService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int DELIVERY_BATCH_SIZE = 16;
    private static final long LEASE_SECONDS = 300;

    private final OutboxEventRepository eventRepository;
    private final OutboxRecipientDeliveryRepository deliveryRepository;
    private final RecipientNotificationTransport notificationTransport;

    public ActivityPublicationNotificationService(
            OutboxEventRepository eventRepository,
            OutboxRecipientDeliveryRepository deliveryRepository,
            RecipientNotificationTransport notificationTransport) {
        this.eventRepository = eventRepository;
        this.deliveryRepository = deliveryRepository;
        this.notificationTransport = notificationTransport;
    }

    @Transactional(readOnly = true)
    public List<UUID> availableEventIds(Instant now) {
        var pending = eventRepository.findByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                List.of(OutboxEventStatus.PENDING), now, PageRequest.of(0, 16));
        var expired = eventRepository.findByStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
                OutboxEventStatus.PROCESSING, now, PageRequest.of(0, DELIVERY_BATCH_SIZE));
        return java.util.stream.Stream.concat(pending.stream(), expired.stream()).map(event -> event.getId()).distinct().toList();
    }

    @Transactional(readOnly = true)
    public BacklogSnapshot backlog() {
        return new BacklogSnapshot(
                deliveryRepository.countByStatus(OutboxRecipientDeliveryStatus.PENDING),
                deliveryRepository.countByStatusAndAttemptCountGreaterThan(OutboxRecipientDeliveryStatus.PENDING, 0));
    }

    @Transactional
    public boolean claimEvent(UUID eventId, Instant now) {
        var event = eventRepository.findLockedById(eventId).orElse(null);
        if (event == null || event.getStatus() == OutboxEventStatus.PUBLISHED || event.getStatus() == OutboxEventStatus.FAILED) {
            return false;
        }
        if (event.getStatus() == OutboxEventStatus.PROCESSING
                && (event.getLeaseUntil() == null || !event.getLeaseUntil().isBefore(now))) {
            return false;
        }
        event.setStatus(OutboxEventStatus.PROCESSING);
        event.setLeaseUntil(now.plusSeconds(LEASE_SECONDS));
        event.setAttemptCount(event.getAttemptCount() + 1);
        return true;
    }

    @Transactional(readOnly = true)
    public List<UUID> availableDeliveryIds(UUID eventId, Instant now) {
        var pending = deliveryRepository.findByOutboxEvent_IdAndStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                eventId, List.of(OutboxRecipientDeliveryStatus.PENDING), now, PageRequest.of(0, DELIVERY_BATCH_SIZE));
        var expiredProcessing = deliveryRepository.findByOutboxEvent_IdAndStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
                eventId, OutboxRecipientDeliveryStatus.PROCESSING, now, PageRequest.of(0, DELIVERY_BATCH_SIZE)).stream();
        var expiredSending = deliveryRepository.findByOutboxEvent_IdAndStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
                eventId, OutboxRecipientDeliveryStatus.SENDING, now, PageRequest.of(0, DELIVERY_BATCH_SIZE)).stream();
        return java.util.stream.Stream.of(pending.stream(), expiredProcessing, expiredSending)
                .flatMap(java.util.function.Function.identity())
                .map(OutboxRecipientDelivery::getId)
                .distinct()
                .limit(DELIVERY_BATCH_SIZE)
                .toList();
    }

    @Transactional
    public DeliveryMessage claimDelivery(UUID deliveryId, Instant now) {
        var delivery = deliveryRepository.findLockedById(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() == OutboxRecipientDeliveryStatus.SENT
                || delivery.getStatus() == OutboxRecipientDeliveryStatus.FAILED) {
            return null;
        }
        if (delivery.getStatus() == OutboxRecipientDeliveryStatus.SENDING) {
            if (delivery.getLeaseUntil() != null && delivery.getLeaseUntil().isBefore(now)) {
                markUncertain(delivery, "LEASE_EXPIRED_AFTER_SEND_BOUNDARY", now);
            }
            return null;
        }
        if (delivery.getStatus() == OutboxRecipientDeliveryStatus.PROCESSING
                && (delivery.getLeaseUntil() == null || !delivery.getLeaseUntil().isBefore(now))) {
            return null;
        }
        if (delivery.getStatus() == OutboxRecipientDeliveryStatus.PROCESSING) {
            delivery.setStatus(OutboxRecipientDeliveryStatus.PENDING);
            delivery.setLeaseUntil(null);
        }
        delivery.setStatus(OutboxRecipientDeliveryStatus.PROCESSING);
        delivery.setLeaseUntil(now.plusSeconds(LEASE_SECONDS));
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        var activity = delivery.getOutboxEvent().getAggregateId();
        var recipient = delivery.getGroupClassMember().getTenantAccount().getAccount().getEmail();
        return new DeliveryMessage(delivery.getId(), recipient, activity, delivery.getIdempotencyKey());
    }

    @Transactional
    public DeliveryMessage beginSend(UUID deliveryId, Instant now) {
        var delivery = deliveryRepository.findLockedById(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() != OutboxRecipientDeliveryStatus.PROCESSING) {
            return null;
        }
        delivery.setStatus(OutboxRecipientDeliveryStatus.SENDING);
        delivery.setLeaseUntil(now.plusSeconds(LEASE_SECONDS));
        var activity = delivery.getOutboxEvent().getAggregateId();
        var recipient = delivery.getGroupClassMember().getTenantAccount().getAccount().getEmail();
        return new DeliveryMessage(delivery.getId(), recipient, activity, delivery.getIdempotencyKey());
    }

    public RecipientNotificationTransport.DeliveryOutcome send(DeliveryMessage message) {
        return notificationTransport.deliver(message);
    }

    @Transactional
    public void markDelivered(UUID deliveryId, Instant now) {
        var delivery = deliveryRepository.findLockedById(deliveryId).orElseThrow();
        delivery.setStatus(OutboxRecipientDeliveryStatus.SENT);
        delivery.setLeaseUntil(null);
        delivery.setSentAt(now);
        completeEventIfFinished(delivery.getOutboxEvent().getId(), now);
    }

    @Transactional
    public DeliveryFailureResult markDeliveryFailed(UUID deliveryId, Instant now) {
        var delivery = deliveryRepository.findLockedById(deliveryId).orElseThrow();
        var terminal = delivery.getAttemptCount() >= MAX_ATTEMPTS;
        delivery.setStatus(terminal ? OutboxRecipientDeliveryStatus.FAILED : OutboxRecipientDeliveryStatus.PENDING);
        delivery.setLeaseUntil(null);
        delivery.setAvailableAt(now.plusSeconds(terminal ? 0 : delivery.getAttemptCount() * 5L));
        delivery.setLastErrorCode("EMAIL_DELIVERY_FAILED");
        completeEventIfFinished(delivery.getOutboxEvent().getId(), now);
        return new DeliveryFailureResult(terminal);
    }

    @Transactional
    public void markDeliveryUncertain(UUID deliveryId, Instant now) {
        var delivery = deliveryRepository.findLockedById(deliveryId).orElseThrow();
        markUncertain(delivery, "SMTP_ACCEPTANCE_UNCERTAIN", now);
        completeEventIfFinished(delivery.getOutboxEvent().getId(), now);
    }

    @Transactional
    public void releaseEvent(UUID eventId, Instant now) {
        var event = eventRepository.findLockedById(eventId).orElseThrow();
        settleEvent(event, now);
        event.setLeaseUntil(null);
    }

    private void completeEventIfFinished(UUID eventId, Instant now) {
        var event = eventRepository.findLockedById(eventId).orElseThrow();
        settleEvent(event, now);
    }

    private void settleEvent(com.wornux.data.entities.training_activity.OutboxEvent event, Instant now) {
        var eventId = event.getId();
        if (deliveryRepository.countByOutboxEvent_IdAndStatusNot(eventId, OutboxRecipientDeliveryStatus.SENT) == 0) {
            event.setStatus(OutboxEventStatus.PUBLISHED);
            event.setPublishedAt(now);
            return;
        }
        var hasRetryableDelivery = deliveryRepository.countByOutboxEvent_IdAndStatus(eventId, OutboxRecipientDeliveryStatus.PENDING) > 0
                || deliveryRepository.countByOutboxEvent_IdAndStatus(eventId, OutboxRecipientDeliveryStatus.PROCESSING) > 0;
        if (hasRetryableDelivery) {
            event.setStatus(OutboxEventStatus.PENDING);
            event.setAvailableAt(now.plusSeconds(5));
            return;
        }
        event.setStatus(OutboxEventStatus.FAILED);
        event.setLastErrorCode("RECIPIENT_DELIVERY_EXHAUSTED");
    }

    private void markUncertain(OutboxRecipientDelivery delivery, String errorCode, Instant now) {
        delivery.setStatus(OutboxRecipientDeliveryStatus.UNCERTAIN);
        delivery.setLeaseUntil(null);
        delivery.setAvailableAt(now);
        delivery.setLastErrorCode(errorCode);
    }

    public record DeliveryMessage(UUID deliveryId, String emailAddress, UUID activityId, String idempotencyKey) {
    }

    public record DeliveryFailureResult(boolean exhausted) {
    }

    public record BacklogSnapshot(long pending, long retryable) {
    }
}
