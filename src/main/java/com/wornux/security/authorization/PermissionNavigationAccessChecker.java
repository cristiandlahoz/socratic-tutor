package com.wornux.security.authorization;

import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationContext;
import com.wornux.services.context.ContextSelectionResult;
import com.wornux.services.context.ContextSelectionService;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import jakarta.annotation.security.PermitAll;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class PermissionNavigationAccessChecker implements NavigationAccessChecker {

    private final transient AuthorizationService authorizationService;
    private final transient ActiveContextHolder activeContextHolder;
    private final transient AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final transient ContextSelectionService contextSelectionService;

    public PermissionNavigationAccessChecker(
            AuthorizationService authorizationService,
            ActiveContextHolder activeContextHolder,
            AuthenticatedUserContextUtils authenticatedUserContextUtils,
            ContextSelectionService contextSelectionService) {
        this.authorizationService = authorizationService;
        this.activeContextHolder = activeContextHolder;
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
        this.contextSelectionService = contextSelectionService;
    }

    @Override
    public AccessCheckResult check(NavigationContext context) {
        if (context.isErrorHandling()) {
            return AccessCheckResult.neutral();
        }
        var target = context.getNavigationTarget();
        if (AnnotationUtils.findAnnotation(target, AnonymousAllowed.class) != null) {
            return AccessCheckResult.allow();
        }
        if (context.getPrincipal() == null) {
            return AccessCheckResult.deny("Authentication is required");
        }
        var permission = AnnotationUtils.findAnnotation(target, RequiresPermission.class);
        if (permission != null) {
            try {
                var contextResult = ensureActiveContext();
                if (contextResult != null) {
                    return contextResult;
                }
                return authorizationService.can(permission.value())
                        ? AccessCheckResult.allow()
                        : AccessCheckResult.deny("Missing permission " + permission.value().code());
            }
            catch (AccessDeniedException exception) {
                return AccessCheckResult.deny(exception.getMessage());
            }
        }
        if (target.getPackageName().startsWith("com.wornux.ui.auth")
                && AnnotationUtils.findAnnotation(target, PermitAll.class) != null) {
            return AccessCheckResult.allow();
        }
        return AccessCheckResult.deny("Protected routes require @RequiresPermission");
    }

    private AccessCheckResult ensureActiveContext() {
        if (activeContextHolder.current().isPresent()) {
            return null;
        }
        var account = authenticatedUserContextUtils.currentAccount().orElse(null);
        if (account == null) {
            return AccessCheckResult.deny("Authentication is required");
        }
        return switch (contextSelectionService.resolveLoginContext(account)) {
            case ContextSelectionResult.Selected _ -> null;
            case ContextSelectionResult.NoAccess _ -> AccessCheckResult.deny("No available context");
            case ContextSelectionResult.SelectionRequired _ -> AccessCheckResult.deny("Context selection is required");
        };
    }
}
