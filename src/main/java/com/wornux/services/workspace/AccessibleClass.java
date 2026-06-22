package com.wornux.services.workspace;

import com.wornux.data.entities.academic.GroupClassMemberRole;
import java.util.UUID;

public record AccessibleClass(
        UUID groupClassId,
        UUID groupClassMemberId,
        UUID tenantAccountId,
        String tenantName,
        String classCode,
        String className,
        GroupClassMemberRole role) {}
