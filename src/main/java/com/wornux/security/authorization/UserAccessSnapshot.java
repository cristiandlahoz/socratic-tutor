package com.wornux.security.authorization;

import java.util.Set;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.security.permission.AppPermission;

public record UserAccessSnapshot(UUID accountId, ActiveContext activeContext, UUID tenantId, UUID tenantAccountId,
        UUID groupClassId, UUID groupClassMemberId, GroupClassMemberKind memberKind, Set<String> roleCodes,
        Set<String> permissionCodes, long roleNamespaceVersion) {

    public boolean hasPermission(String code) {
        if (permissionCodes.contains(code)) {
            return true;
        }
        return AppPermission.fromCode(code)
                .map(this::hasImpliedPermission)
                .orElse(false);
    }

    private boolean hasImpliedPermission(AppPermission requested) {
        return permissionCodes.stream()
                .map(AppPermission::fromCode)
                .flatMap(java.util.Optional::stream)
                .anyMatch(granted -> granted.grants(requested));
    }
}
