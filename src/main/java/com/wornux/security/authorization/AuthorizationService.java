package com.wornux.security.authorization;

import com.wornux.security.AuthenticatedAccountDetails;
import com.wornux.security.permission.AppPermission;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private final ActiveContextHolder activeContextHolder;
    private final AccessSnapshotService accessSnapshotService;

    public AuthorizationService(ActiveContextHolder activeContextHolder, AccessSnapshotService accessSnapshotService) {
        this.activeContextHolder = activeContextHolder;
        this.accessSnapshotService = accessSnapshotService;
    }

    public boolean can(AppPermission permission) {
        return snapshot().hasPermission(permission.code());
    }

    public void check(AppPermission permission) {
        if (!can(permission)) {
            throw new AccessDeniedException("Missing permission %s".formatted(permission.code()));
        }
    }

    public UserAccessSnapshot snapshot() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedAccountDetails details)) {
            throw new AccessDeniedException("Authentication is required");
        }
        var activeContext = activeContextHolder.current()
                .orElseThrow(() -> new AccessDeniedException("Active context is required"));
        return accessSnapshotService.snapshot(details.principal().accountId(), activeContext);
    }
}
