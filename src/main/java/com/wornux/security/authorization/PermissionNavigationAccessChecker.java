package com.wornux.security.authorization;

import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

@Component
public class PermissionNavigationAccessChecker implements NavigationAccessChecker {

    public PermissionNavigationAccessChecker() {
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
        if (RouterLayout.class.isAssignableFrom(target)
                && AnnotationUtils.findAnnotation(target, PermitAll.class) != null) {
            return AccessCheckResult.allow();
        }
        if (context.getPrincipal() == null) {
            return AccessCheckResult.deny("Authentication is required");
        }
        if (AnnotationUtils.findAnnotation(target, RequiresPermission.class) != null) {
            return AccessCheckResult.allow();
        }
        if (target.getPackageName().startsWith("com.wornux.ui.auth")
                && AnnotationUtils.findAnnotation(target, PermitAll.class) != null) {
            return AccessCheckResult.allow();
        }
        return AccessCheckResult.deny("Protected routes require @RequiresPermission");
    }

}
