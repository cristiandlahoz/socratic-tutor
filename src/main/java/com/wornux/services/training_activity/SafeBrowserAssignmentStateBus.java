package com.wornux.services.training_activity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

@Component
public class SafeBrowserAssignmentStateBus {

    private final List<Consumer<Notification>> listeners = new CopyOnWriteArrayList<>();

    public AutoCloseable subscribe(Consumer<Notification> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void publish(Notification notification) {
        for (var listener : listeners) {
            listener.accept(notification);
        }
    }

    public record Notification(
            UUID assignmentId,
            UUID groupClassMemberId,
            boolean locked) {

        public boolean affectsAssignment(UUID targetAssignmentId) {
            return targetAssignmentId != null && targetAssignmentId.equals(assignmentId);
        }
    }
}
