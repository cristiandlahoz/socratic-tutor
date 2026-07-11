package com.wornux.services.training_activity;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.OutboxEventStatus;
import com.wornux.data.entities.training_activity.OutboxRecipientDeliveryStatus;
import com.wornux.data.repositories.training_activity.OutboxEventRepository;
import com.wornux.data.repositories.training_activity.OutboxRecipientDeliveryRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, audited recovery for deliveries that cannot safely be retried automatically. */
@Service
public class ActivityPublicationDeliveryRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityPublicationDeliveryRecoveryService.class);

    private final OutboxEventRepository eventRepository;
    private final OutboxRecipientDeliveryRepository deliveryRepository;
    private final TrainingActivityRepository activityRepository;
    private final ActiveAcademicContextResolver contextResolver;
    private final AuthorizationService authorizationService;

    public ActivityPublicationDeliveryRecoveryService(
            OutboxEventRepository eventRepository,
            OutboxRecipientDeliveryRepository deliveryRepository,
            TrainingActivityRepository activityRepository,
            ActiveAcademicContextResolver contextResolver,
            AuthorizationService authorizationService) {
        this.eventRepository = eventRepository;
        this.deliveryRepository = deliveryRepository;
        this.activityRepository = activityRepository;
        this.contextResolver = contextResolver;
        this.authorizationService = authorizationService;
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_UPDATE)
    public void replay(UUID deliveryId) {
        authorizationService.check(AppPermission.TRAINING_ACTIVITY_UPDATE);
        var context = contextResolver.requireCurrent();
        if (context.groupClassKind() != GroupClassMemberKind.PROFESSOR) {
            throw new SetupRequiredException("An active professor class context is required before replaying a delivery.");
        }
        var deliveryReference = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification delivery %s".formatted(deliveryId)));
        var eventId = deliveryReference.getOutboxEvent().getId();
        var event = eventRepository.findLockedById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification outbox event."));
        var delivery = deliveryRepository.findLockedById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification delivery %s".formatted(deliveryId)));
        if (!eventId.equals(delivery.getOutboxEvent().getId())) {
            throw new IllegalStateException("Notification delivery changed outbox event during replay.");
        }
        var activity = activityRepository.findById(event.getAggregateId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown published activity for notification delivery."));
        if (!context.groupClassId().equals(activity.getGroupClass().getId())) {
            throw new SecurityException("The notification delivery is outside the active class context.");
        }
        if (event.getStatus() == OutboxEventStatus.PUBLISHED) {
            throw new IllegalStateException("Published notification events cannot be replayed.");
        }
        var priorStatus = delivery.getStatus();
        var deliveryIsAlreadyRetryable = priorStatus == OutboxRecipientDeliveryStatus.PENDING;
        if (!deliveryIsAlreadyRetryable && priorStatus != OutboxRecipientDeliveryStatus.FAILED
                && priorStatus != OutboxRecipientDeliveryStatus.UNCERTAIN) {
            throw new IllegalStateException("Only terminal or uncertain notification deliveries can be replayed.");
        }
        var now = Instant.now();
        if (!deliveryIsAlreadyRetryable) {
            delivery.setStatus(OutboxRecipientDeliveryStatus.PENDING);
            delivery.setAttemptCount(0);
            delivery.setLeaseUntil(null);
            delivery.setAvailableAt(now);
            delivery.setLastErrorCode("MANUAL_REPLAY_REQUESTED");
        }
        var eventReactivated = event.getStatus() == OutboxEventStatus.FAILED;
        if (eventReactivated) {
            event.setStatus(OutboxEventStatus.PENDING);
            event.setLeaseUntil(null);
            event.setAvailableAt(now);
            event.setLastErrorCode("MANUAL_REPLAY_REQUESTED");
        }
        LOGGER.info("Notification delivery manually replayed: priorStatus={} eventReactivated={}", priorStatus, eventReactivated);
    }
}
