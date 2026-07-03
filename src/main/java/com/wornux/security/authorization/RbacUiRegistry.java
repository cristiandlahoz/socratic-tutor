package com.wornux.security.authorization;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.vaadin.flow.component.UI;
import org.springframework.stereotype.Component;

@Component
public class RbacUiRegistry {

    private final Set<RegisteredUi> registeredUis = ConcurrentHashMap.newKeySet();

    public Registration register(UI ui, UUID roleNamespaceId, Runnable refreshAction) {
        var registeredUi = new RegisteredUi(ui, roleNamespaceId, refreshAction);
        registeredUis.add(registeredUi);
        return () -> registeredUis.remove(registeredUi);
    }

    Set<RegisteredUi> affectedBy(UUID roleNamespaceId) {
        registeredUis.removeIf(registeredUi -> !registeredUi.ui().isAttached());
        return registeredUis.stream()
                .filter(registeredUi -> registeredUi.roleNamespaceId().equals(roleNamespaceId))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public interface Registration {
        void remove();
    }

    record RegisteredUi(UI ui, UUID roleNamespaceId, Runnable refreshAction) {
    }
}
