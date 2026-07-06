package com.wornux.security.authorization;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RbacUiBroadcaster {

    private final AccessSnapshotService accessSnapshotService;
    private final RbacUiRegistry rbacUiRegistry;

    public RbacUiBroadcaster(AccessSnapshotService accessSnapshotService, RbacUiRegistry rbacUiRegistry) {
        this.accessSnapshotService = accessSnapshotService;
        this.rbacUiRegistry = rbacUiRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRbacChanged(RbacChangedEvent event) {
        accessSnapshotService.invalidateNamespace(event.roleNamespaceId());
        rbacUiRegistry.attachedUis().forEach(registeredUi ->
            registeredUi.ui().access(() -> registeredUi.refreshAction().accept(event.roleNamespaceId())));
    }
}
