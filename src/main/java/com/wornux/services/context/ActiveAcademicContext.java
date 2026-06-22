package com.wornux.services.context;

import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMemberRole;

public record ActiveAcademicContext(UUID accountId, UUID tenantAccountId, UUID groupClassMemberId, UUID groupClassId,
        GroupClassMemberRole groupClassRole) {}
