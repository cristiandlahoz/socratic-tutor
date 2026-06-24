package com.wornux.data.repositories.academic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupClassMemberRepository extends JpaRepository<GroupClassMember, UUID> {
    Optional<GroupClassMember> findByIdAndTenantAccount_Id(UUID groupClassMemberId, UUID tenantAccountId);

    Optional<GroupClassMember> findByGroupClass_IdAndTenantAccount_Id(UUID groupClassId, UUID tenantAccountId);

    List<GroupClassMember> findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(UUID accountId);

    List<GroupClassMember> findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(UUID groupClassId);

    Optional<GroupClassMember> findByGroupClass_IdAndTenantAccount_Account_IdAndRoleAndLockedFalse(
            UUID groupClassId,
            UUID accountId,
            com.wornux.data.entities.academic.GroupClassMemberRole role);
}
