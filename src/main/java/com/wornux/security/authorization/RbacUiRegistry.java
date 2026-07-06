package com.wornux.security.authorization;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.vaadin.flow.component.UI;
import org.springframework.stereotype.Component;

@Component
public class RbacUiRegistry {

    private final Set<RegisteredUi> registeredUis = ConcurrentHashMap.newKeySet();

    public Registration register(UI ui, Consumer<UUID> refreshAction) {
        var registeredUi = new RegisteredUi(ui, refreshAction);
        registeredUis.add(registeredUi);
        return () -> registeredUis.remove(registeredUi);
    }

    Set<RegisteredUi> attachedUis() {
        registeredUis.removeIf(registeredUi -> !registeredUi.ui().isAttached());
        return Set.copyOf(registeredUis);
    }

    public interface Registration {
        void remove();
    }

    record RegisteredUi(UI ui, Consumer<UUID> refreshAction) {
    }
}
