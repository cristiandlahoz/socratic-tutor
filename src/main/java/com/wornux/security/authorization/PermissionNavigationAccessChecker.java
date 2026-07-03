package com.wornux.security.authorization;

import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class PermissionNavigationAccessChecker implements NavigationAccessChecker {

    private final AuthorizationService authorizationService;

    public PermissionNavigationAccessChecker(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
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
}
