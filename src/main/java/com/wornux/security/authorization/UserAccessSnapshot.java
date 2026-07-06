package com.wornux.security.authorization;

import java.util.Set;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMemberKind;

public record UserAccessSnapshot(UUID accountId, ActiveContext activeContext, UUID tenantId, UUID tenantAccountId,
        UUID groupClassId, UUID groupClassMemberId, GroupClassMemberKind memberKind, Set<String> roleCodes,
        Set<String> permissionCodes, long roleNamespaceVersion) {

    public boolean hasPermission(String code) {
        return permissionCodes.contains(code);
    }
}
