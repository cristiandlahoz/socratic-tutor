package com.wornux.data.repositories.academic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupClassMemberRepository extends JpaRepository<GroupClassMember, UUID> {
    @EntityGraph(attributePaths = { "groupClass", "groupClass.tenant" })
    Optional<GroupClassMember> findByIdAndTenantAccount_Id(UUID groupClassMemberId, UUID tenantAccountId);

    @EntityGraph(attributePaths = { "groupClass", "groupClass.tenant", "tenantAccount", "tenantAccount.tenant" })
    Optional<GroupClassMember> findByGroupClass_IdAndTenantAccount_Id(UUID groupClassId, UUID tenantAccountId);

    @EntityGraph(attributePaths = { "tenantAccount", "tenantAccount.tenant", "groupClass", "groupClass.tenant" })
    List<GroupClassMember> findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(UUID accountId);

    @EntityGraph(attributePaths = { "tenantAccount", "tenantAccount.account" })
    List<GroupClassMember> findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(UUID groupClassId);

    Optional<GroupClassMember> findByGroupClass_IdAndTenantAccount_Account_IdAndLockedFalse(
            UUID groupClassId,
            UUID accountId);

    Optional<GroupClassMember> findByGroupClass_IdAndTenantAccount_Account_IdAndMemberKindAndLockedFalse(
            UUID groupClassId,
            UUID accountId,
            GroupClassMemberKind memberKind);
}
