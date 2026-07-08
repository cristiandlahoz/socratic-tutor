package com.wornux.services.security;

import java.util.Optional;
import java.util.function.Supplier;

import com.wornux.data.entities.authorization.Role;

public final class RoleAssignmentUtils {

    private RoleAssignmentUtils() {
    }

    public static void createIfMissing(
            Role role,
            Supplier<Optional<?>> existingAssignment,
            Runnable createAssignment,
            RoleNamespaceService roleNamespaceService) {
        if (existingAssignment.get().isPresent()) {
            return;
        }
        createAssignment.run();
        roleNamespaceService.recordRbacChange(role.getRoleNamespace().getId());
    }
}
