package com.wornux.security.authorization;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.wornux.services.context.ContextSelectionResult;
import com.wornux.services.context.ContextSelectionService;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.auth.NoAccessView;
import jakarta.annotation.security.PermitAll;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class PermissionBeforeEnterListener implements VaadinServiceInitListener {

    private final AuthorizationService authorizationService;
    private final ActiveContextHolder activeContextHolder;
    private final AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final ContextSelectionService contextSelectionService;
    private final WorkspaceRoutingService workspaceRoutingService;

    public PermissionBeforeEnterListener(
            AuthorizationService authorizationService,
            ActiveContextHolder activeContextHolder,
            AuthenticatedUserContextUtils authenticatedUserContextUtils,
            ContextSelectionService contextSelectionService,
            WorkspaceRoutingService workspaceRoutingService) {
        this.authorizationService = authorizationService;
        this.activeContextHolder = activeContextHolder;
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
        this.contextSelectionService = contextSelectionService;
        this.workspaceRoutingService = workspaceRoutingService;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent -> uiEvent.getUI().addBeforeEnterListener(this::checkAccess));
    }

    private void checkAccess(BeforeEnterEvent event) {
        if (event.isErrorEvent() || event.getNavigationTarget().equals(NoAccessView.class)) {
            return;
        }
        var target = event.getNavigationTarget();
        if (AnnotationUtils.findAnnotation(target, AnonymousAllowed.class) != null || isPermittedAuthView(target)) {
            return;
        }
        var account = authenticatedUserContextUtils.currentAccount().orElse(null);
        if (account == null) {
            return;
        }
        var permission = AnnotationUtils.findAnnotation(target, RequiresPermission.class);
        if (permission == null) {
            event.forwardTo(NoAccessView.class);
            return;
        }
        try {
            if (!ensureActiveContext(account) || !prepareWorkspaceAccess(permission.workspace())
                    || !authorizationService.can(permission.value())) {
                event.forwardTo(NoAccessView.class);
            }
        }
        catch (AccessDeniedException | SecurityException exception) {
            event.forwardTo(NoAccessView.class);
        }
    }

    private boolean ensureActiveContext(com.wornux.data.entities.identity.Account account) {
        if (activeContextHolder.current().isPresent()) {
            return true;
        }
        return switch (contextSelectionService.resolveLoginContext(account)) {
            case ContextSelectionResult.Selected _ -> true;
            case ContextSelectionResult.NoAccess _, ContextSelectionResult.SelectionRequired _ -> false;
        };
    }

    private boolean prepareWorkspaceAccess(WorkspaceDestination workspaceDestination) {
        if (workspaceDestination == WorkspaceDestination.NO_ACCESS) {
            return true;
        }
        return workspaceRoutingService.prepareWorkspaceAccess(
                authenticatedUserContextUtils.requireCurrentAccount(),
                workspaceDestination);
    }

    private boolean isPermittedAuthView(Class<?> target) {
        return target.getPackageName().startsWith("com.wornux.ui.auth")
                && AnnotationUtils.findAnnotation(target, PermitAll.class) != null;
    }
}
