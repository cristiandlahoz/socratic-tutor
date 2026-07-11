package com.wornux.specdriven.usecases.uc008_publish_and_deliver_training_activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.services.training_activity.ActivityPublicationNotificationMetrics;
import com.wornux.services.training_activity.ActivityPublicationNotificationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ActivityPublicationNotificationMetricsTest {

    @Test
    void resilience_centralMetricsExposeBoundedCountersBacklogAndProcessingDuration() {
        var registry = new SimpleMeterRegistry();
        var metrics = new ActivityPublicationNotificationMetrics(registry);

        metrics.recordPoll();
        metrics.recordPollFailure();
        metrics.recordEventClaimed();
        metrics.recordDeliverySuccess();
        metrics.recordDeliveryFailure();
        metrics.recordDeliveryRetry();
        metrics.recordDeliveryExhausted();
        metrics.updateBacklog(new ActivityPublicationNotificationService.BacklogSnapshot(7, 3));
        metrics.recordProcessing(() -> {});

        assertThat(registry.get(ActivityPublicationNotificationMetrics.POLLS).counter().count()).isEqualTo(1);
        assertThat(registry.get(ActivityPublicationNotificationMetrics.POLL_FAILURES).counter().count()).isEqualTo(1);
        assertThat(registry.get(ActivityPublicationNotificationMetrics.EVENTS_CLAIMED).counter().count()).isEqualTo(1);
        assertThat(registry.get(ActivityPublicationNotificationMetrics.DELIVERY_SUCCESS).counter().count()).isEqualTo(1);
        assertThat(registry.get(ActivityPublicationNotificationMetrics.DELIVERY_FAILURE).counter().count()).isEqualTo(1);
        assertThat(registry.get(ActivityPublicationNotificationMetrics.DELIVERY_RETRIES).counter().count()).isEqualTo(1);
        assertThat(registry.get(ActivityPublicationNotificationMetrics.DELIVERY_EXHAUSTED).counter().count()).isEqualTo(1);
        assertThat(registry.get(ActivityPublicationNotificationMetrics.BACKLOG).tag("state", "pending").gauge().value()).isEqualTo(7);
        assertThat(registry.get(ActivityPublicationNotificationMetrics.BACKLOG).tag("state", "retryable").gauge().value()).isEqualTo(3);
        assertThat(registry.get(ActivityPublicationNotificationMetrics.PROCESSING_DURATION).timer().count()).isEqualTo(1);
    }
}
