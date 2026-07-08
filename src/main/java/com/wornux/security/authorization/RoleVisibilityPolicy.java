package com.wornux.security.authorization;

import java.util.UUID;

import com.wornux.data.entities.authorization.Role;
import com.wornux.data.entities.authorization.ScopeLevel;
import org.springframework.security.access.AccessDeniedException;

public final class RoleVisibilityPolicy {

    private RoleVisibilityPolicy() {
    }

    public static Role requireVisible(
            Role role,
            UUID activeNamespaceId,
            ScopeLevel expectedLevel,
            boolean requireAssignable,
            String deniedMessage) {
        if (!role.getRoleNamespace().getId().equals(activeNamespaceId)) {
            throw new AccessDeniedException("Role is outside the active namespace");
        }
        if (role.getAssignmentLevel() != expectedLevel || !role.isActive()
                || (requireAssignable && !role.isAssignable())) {
            throw new AccessDeniedException(deniedMessage.formatted(expectedLevel));
        }
        return role;
    }
}
