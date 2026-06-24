package com.wornux.services.workspace;

import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMemberRole;

public record AccessibleClass(UUID groupClassId, UUID groupClassMemberId, UUID tenantAccountId, String tenantName,
        String classCode, String className, GroupClassMemberRole role) {}
