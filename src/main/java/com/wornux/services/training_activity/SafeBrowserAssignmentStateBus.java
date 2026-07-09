package com.wornux.services.training_activity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SafeBrowserAssignmentStateBus {

    private static final Logger LOGGER = LoggerFactory.getLogger(SafeBrowserAssignmentStateBus.class);

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
                LOGGER.warn(
                        "Safe Browser assignment state listener failed: assignmentId={} trainingActivityId={}",
                        notification == null ? null : notification.assignmentId(),
                        notification == null ? null : notification.trainingActivityId(),
                        exception);
            }
        }
    }

    public record Notification(
            UUID trainingActivityId,
            UUID assignmentId,
            UUID groupClassMemberId,
            boolean locked,
            boolean activityClosed) {

        public boolean affectsAssignment(UUID targetAssignmentId) {
            return targetAssignmentId != null && targetAssignmentId.equals(assignmentId);
        }

        public boolean affectsTrainingActivity(UUID targetTrainingActivityId) {
            return targetTrainingActivityId != null && targetTrainingActivityId.equals(trainingActivityId);
        }
    }
}
