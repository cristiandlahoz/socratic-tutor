package com.wornux.services.training_activity;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** Stable, low-cardinality observability for the publication notification worker. */
@Component
public class ActivityPublicationNotificationMetrics {

    public static final String POLLS = "training.activity.notification.polls";
    public static final String POLL_FAILURES = "training.activity.notification.poll.failures";
    public static final String EVENTS_CLAIMED = "training.activity.notification.events.claimed";
    public static final String DELIVERY_SUCCESS = "training.activity.notification.delivery.success";
    public static final String DELIVERY_FAILURE = "training.activity.notification.delivery.failure";
    public static final String DELIVERY_RETRIES = "training.activity.notification.delivery.retries";
    public static final String DELIVERY_EXHAUSTED = "training.activity.notification.delivery.exhausted";
    public static final String BACKLOG = "training.activity.notification.backlog";
    public static final String PROCESSING_DURATION = "training.activity.notification.processing.duration";

    private final Counter polls;
    private final Counter pollFailures;
    private final Counter eventsClaimed;
    private final Counter deliverySuccess;
    private final Counter deliveryFailure;
    private final Counter deliveryRetries;
    private final Counter deliveryExhausted;
    private final AtomicLong pendingBacklog = new AtomicLong();
    private final AtomicLong retryableBacklog = new AtomicLong();
    private final Timer processingDuration;

    public ActivityPublicationNotificationMetrics(MeterRegistry meterRegistry) {
        polls = meterRegistry.counter(POLLS);
        pollFailures = meterRegistry.counter(POLL_FAILURES);
        eventsClaimed = meterRegistry.counter(EVENTS_CLAIMED);
        deliverySuccess = meterRegistry.counter(DELIVERY_SUCCESS);
        deliveryFailure = meterRegistry.counter(DELIVERY_FAILURE);
        deliveryRetries = meterRegistry.counter(DELIVERY_RETRIES);
        deliveryExhausted = meterRegistry.counter(DELIVERY_EXHAUSTED);
        Gauge.builder(BACKLOG, pendingBacklog, AtomicLong::get).tag("state", "pending").register(meterRegistry);
        Gauge.builder(BACKLOG, retryableBacklog, AtomicLong::get).tag("state", "retryable").register(meterRegistry);
        processingDuration = meterRegistry.timer(PROCESSING_DURATION);
    }

    public void recordPoll() {
        polls.increment();
    }

    public void recordPollFailure() {
        pollFailures.increment();
    }

    public void recordEventClaimed() {
        eventsClaimed.increment();
    }

    public void recordDeliverySuccess() {
        deliverySuccess.increment();
    }

    public void recordDeliveryFailure() {
        deliveryFailure.increment();
    }

    public void recordDeliveryRetry() {
        deliveryRetries.increment();
    }

    public void recordDeliveryExhausted() {
        deliveryExhausted.increment();
    }

    public void updateBacklog(ActivityPublicationNotificationService.BacklogSnapshot backlog) {
        pendingBacklog.set(backlog.pending());
        retryableBacklog.set(backlog.retryable());
    }

    public void recordProcessing(Runnable work) {
        processingDuration.record(work);
    }
}
