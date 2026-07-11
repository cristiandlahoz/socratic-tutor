package com.wornux.services.training_activity;

public interface RecipientNotificationTransport {

    DeliveryOutcome deliver(ActivityPublicationNotificationService.DeliveryMessage message);

    enum DeliveryOutcome {
        ACCEPTED,
        RETRYABLE_BEFORE_ACCEPTANCE,
        UNCERTAIN_AFTER_SEND
    }
}
