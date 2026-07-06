package com.wornux.security.authorization;

import java.util.UUID;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.ui.MainLayout;
import com.wornux.ui.auth.NoAccessView;
import org.springframework.stereotype.Service;

@Service
public class RbacUiRefreshService implements VaadinServiceInitListener {

    private final ActiveContextHolder activeContextHolder;
    private final AccessSnapshotService accessSnapshotService;
    private final AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final RbacUiRegistry rbacUiRegistry;

    public RbacUiRefreshService(
            ActiveContextHolder activeContextHolder,
            AccessSnapshotService accessSnapshotService,
            AuthenticatedUserContextUtils authenticatedUserContextUtils,
            RbacUiRegistry rbacUiRegistry) {
        this.activeContextHolder = activeContextHolder;
        this.accessSnapshotService = accessSnapshotService;
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
        this.rbacUiRegistry = rbacUiRegistry;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(initEvent -> {
            var ui = initEvent.getUI();
            var registration = rbacUiRegistry.register(ui, changedNamespaceId -> refreshUi(ui, changedNamespaceId));
            ui.addDetachListener(_ -> registration.remove());
        });
    }

    private void refreshUi(UI ui, UUID changedNamespaceId) {
        var activeContext = activeContextHolder.current().orElse(null);
        if (activeContext == null || !changedNamespaceId.equals(accessSnapshotService.roleNamespaceId(activeContext))) {
            return;
        }

        authenticatedUserContextUtils.currentAccount()
                .ifPresent(account -> authenticatedUserContextUtils.refreshCurrentAuthentication(account.getId()));

        if (isNoAccessLocation(ui)) {
            ui.navigate("");
            return;
        }

        findMainLayout(ui).ifPresent(layout -> {
            layout.refreshAfterRbacChange();
            if (!layout.currentRouteAllowed()) {
                ui.navigate(NoAccessView.class);
            }
        });
    }

    private boolean isNoAccessLocation(UI ui) {
        return "no-access".equals(ui.getInternals().getActiveViewLocation().getPath());
    }

    private java.util.Optional<MainLayout> findMainLayout(UI ui) {
        return ui.getChildren()
                .flatMap(this::flatten)
                .filter(MainLayout.class::isInstance)
                .map(MainLayout.class::cast)
                .findFirst();
    }

    private Stream<Component> flatten(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(this::flatten));
    }
}
