package com.wornux.services.chat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ChatSessionActivityBus {

    private final Map<String, CopyOnWriteArrayList<Consumer<ChatSessionActivity>>> listeners =
            new ConcurrentHashMap<>();

    public AutoCloseable subscribe(String sessionId, Consumer<ChatSessionActivity> listener) {
        listeners.computeIfAbsent(sessionId, _ -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> unsubscribe(sessionId, listener);
    }

    public void publish(String sessionId, ChatSessionActivity activity) {
        var sessionListeners = listeners.get(sessionId);
        if (hasNoListeners(sessionListeners)) {
            return;
        }
        sessionListeners.forEach(listener -> listener.accept(activity));
    }

    private void unsubscribe(String sessionId, Consumer<ChatSessionActivity> listener) {
        var sessionListeners = listeners.get(sessionId);
        if (hasNoListeners(sessionListeners)) {
            return;
        }
        sessionListeners.remove(listener);
        removeSessionWhenLastListenerLeaves(sessionId, sessionListeners);
    }

    private boolean hasNoListeners(@Nullable CopyOnWriteArrayList<Consumer<ChatSessionActivity>> sessionListeners) {
        return sessionListeners == null || sessionListeners.isEmpty();
    }

    private void removeSessionWhenLastListenerLeaves(
            String sessionId,
            CopyOnWriteArrayList<Consumer<ChatSessionActivity>> sessionListeners) {
        if (sessionListeners.isEmpty()) {
            listeners.remove(sessionId, sessionListeners);
        }
    }
}
