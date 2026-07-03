package com.wornux.security.authorization;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RbacUiBroadcaster {

    private final AccessSnapshotService accessSnapshotService;
    private final RbacUiRegistry rbacUiRegistry;

    public RbacUiBroadcaster(AccessSnapshotService accessSnapshotService, RbacUiRegistry rbacUiRegistry) {
        this.accessSnapshotService = accessSnapshotService;
        this.rbacUiRegistry = rbacUiRegistry;
    }

    @EventListener
    public void onRbacChanged(RbacChangedEvent event) {
        accessSnapshotService.invalidateNamespace(event.roleNamespaceId());
        rbacUiRegistry.affectedBy(event.roleNamespaceId()).forEach(registeredUi ->
            registeredUi.ui().access(() -> registeredUi.refreshAction().run()));
    }
}
