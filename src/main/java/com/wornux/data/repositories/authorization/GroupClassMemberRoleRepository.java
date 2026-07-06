package com.wornux.data.repositories.authorization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.authorization.GroupClassMemberRole;
import com.wornux.data.entities.authorization.GroupClassMemberRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupClassMemberRoleRepository extends JpaRepository<GroupClassMemberRole, GroupClassMemberRoleId> {
    @EntityGraph(attributePaths = { "role", "role.roleNamespace", "groupClassMember", "groupClassMember.groupClass" })
    List<GroupClassMemberRole> findByGroupClassMember_Id(UUID groupClassMemberId);

    List<GroupClassMemberRole> findByGroupClassMember_GroupClass_IdAndRole_Id(UUID groupClassId, UUID roleId);

    long countByGroupClassMember_GroupClass_IdAndGroupClassMember_LockedFalseAndRole_Id(UUID groupClassId, UUID roleId);

    long countByGroupClassMember_GroupClass_Tenant_IdAndGroupClassMember_LockedFalseAndRole_Id(UUID tenantId, UUID roleId);

    Optional<GroupClassMemberRole> findByGroupClassMember_IdAndRole_Code(UUID groupClassMemberId, String roleCode);
}
