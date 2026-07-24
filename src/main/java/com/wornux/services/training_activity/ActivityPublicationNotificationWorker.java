package com.wornux.services.training_activity;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ActivityPublicationNotificationWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityPublicationNotificationWorker.class);

    private final ActivityPublicationNotificationService notificationService;
    private final ActivityPublicationNotificationMetrics metrics;

    public ActivityPublicationNotificationWorker(
            ActivityPublicationNotificationService notificationService, ActivityPublicationNotificationMetrics metrics) {
        this.notificationService = notificationService;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${app.email.activity-notification.poll-ms:15000}")
    public void poll() {
        metrics.recordPoll();
        metrics.recordProcessing(() -> {
            try {
                var eventIds = notificationService.availableEventIds(Instant.now());
                LOGGER.debug("Notification outbox poll completed: eventCount={}", eventIds.size());
                eventIds.forEach(this::deliverEventSafely);
                metrics.updateBacklog(notificationService.backlog());
            }
            catch (RuntimeException exception) {
                metrics.recordPollFailure();
                LOGGER.error("Notification outbox poll failed; the scheduler will continue.");
            }
        });
    }

    private void deliverEventSafely(java.util.UUID eventId) {
        try {
            deliverEvent(eventId);
        }
        catch (RuntimeException exception) {
            metrics.recordPollFailure();
            LOGGER.error("Notification outbox event processing failed; remaining events will continue.");
        }
    }

    private void deliverEvent(java.util.UUID eventId) {
        var now = Instant.now();
        if (!notificationService.claimEvent(eventId, now)) {
            return;
        }
        metrics.recordEventClaimed();
        notificationService.availableDeliveryIds(eventId, now).forEach(deliveryId -> {
            var claimedMessage = notificationService.claimDelivery(deliveryId, Instant.now());
            if (claimedMessage == null) {
                return;
            }
            var message = notificationService.beginSend(claimedMessage.deliveryId(), Instant.now());
            if (message == null) {
                return;
            }
            try {
                var outcome = notificationService.send(message);
                if (outcome == RecipientNotificationTransport.DeliveryOutcome.ACCEPTED) {
                    notificationService.markDelivered(message.deliveryId(), Instant.now());
                    metrics.recordDeliverySuccess();
                    LOGGER.info("Notification delivery accepted.");
                }
                else if (outcome == RecipientNotificationTransport.DeliveryOutcome.RETRYABLE_BEFORE_ACCEPTANCE) {
                    metrics.recordDeliveryFailure();
                    var failure = notificationService.markDeliveryFailed(message.deliveryId(), Instant.now());
                    if (failure.exhausted()) {
                        metrics.recordDeliveryExhausted();
                        LOGGER.error("Notification delivery retries exhausted.");
                    }
                    else {
                        metrics.recordDeliveryRetry();
                        LOGGER.warn("Notification delivery retry scheduled.");
                    }
                }
                else {
                    metrics.recordDeliveryFailure();
                    notificationService.markDeliveryUncertain(message.deliveryId(), Instant.now());
                    LOGGER.error("Notification delivery requires manual replay because SMTP acceptance is uncertain.");
                }
            }
            catch (RuntimeException exception) {
                metrics.recordDeliveryFailure();
                LOGGER.error("Notification worker transport outcome is uncertain after the send boundary.");
                notificationService.markDeliveryUncertain(message.deliveryId(), Instant.now());
            }
        });
        notificationService.releaseEvent(eventId, Instant.now());
    }
}
