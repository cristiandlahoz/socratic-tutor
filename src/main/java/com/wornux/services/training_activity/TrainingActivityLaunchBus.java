package com.wornux.services.training_activity;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

@Component
public class TrainingActivityLaunchBus {

    private final CopyOnWriteArrayList<Consumer<TrainingActivityAssignmentLaunchedEvent>> listeners =
            new CopyOnWriteArrayList<>();

    public AutoCloseable subscribe(Consumer<TrainingActivityAssignmentLaunchedEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void publish(TrainingActivityAssignmentLaunchedEvent event) {
        listeners.forEach(listener -> listener.accept(event));
    }
}
