package com.wornux.data.repositories.authorization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.authorization.GroupClassMemberRole;
import com.wornux.data.entities.authorization.GroupClassMemberRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupClassMemberRoleRepository extends JpaRepository<GroupClassMemberRole, GroupClassMemberRoleId> {
    @EntityGraph(attributePaths = {"role", "role.roleNamespace", "groupClassMember", "groupClassMember.groupClass"})
    List<GroupClassMemberRole> findByGroupClassMember_Id(UUID groupClassMemberId);

    Optional<GroupClassMemberRole> findByGroupClassMember_IdAndRole_Code(UUID groupClassMemberId, String roleCode);
}
