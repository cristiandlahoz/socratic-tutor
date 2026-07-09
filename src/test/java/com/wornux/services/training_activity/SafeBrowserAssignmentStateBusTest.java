package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SafeBrowserAssignmentStateBusTest {

    @Test
    void publishContinuesAfterListenerThrows() {
        var bus = new SafeBrowserAssignmentStateBus();
        var laterListenerCalled = new AtomicBoolean(false);
        var notification = new SafeBrowserAssignmentStateBus.Notification(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                false,
                false);

        bus.subscribe(_ -> {
            throw new IllegalStateException("listener failed");
        });
        bus.subscribe(_ -> laterListenerCalled.set(true));

        bus.publish(notification);

        assertThat(laterListenerCalled.get()).isTrue();
    }
}
