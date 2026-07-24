package com.wornux.services.training_activity;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class TrainingActivityLaunchedBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrainingActivityLaunchedBus.class);

    private final List<Consumer<Notification>> listeners = new CopyOnWriteArrayList<>();

    public AutoCloseable subscribe(Consumer<Notification> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void publish(Notification notification) {
        for (var listener : listeners) {
            try {
                listener.accept(notification);
            }
            catch (RuntimeException exception) {
                LOGGER.warn("Training activity launch listener failed: activityId={}",
                        notification.trainingActivityId(), exception);
            }
        }
    }

    public record Notification(
            UUID trainingActivityId,
            UUID groupClassId,
            Set<UUID> groupClassMemberIds) {

        public boolean affectsGroupClassMember(UUID groupClassMemberId) {
            return groupClassMemberId != null && groupClassMemberIds.contains(groupClassMemberId);
        }
    }
}
