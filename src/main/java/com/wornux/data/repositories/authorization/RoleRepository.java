package com.wornux.data.repositories.authorization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.authorization.Role;
import com.wornux.data.entities.authorization.ScopeLevel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);

    Optional<Role> findByRoleNamespace_IdAndCode(UUID roleNamespaceId, String code);

    @EntityGraph(attributePaths = "roleNamespace")
    List<Role> findByRoleNamespace_IdAndAssignmentLevelAndActiveTrue(
            UUID roleNamespaceId,
            ScopeLevel assignmentLevel);

    @EntityGraph(attributePaths = "roleNamespace")
    List<Role> findByRoleNamespace_IdAndActiveTrue(UUID roleNamespaceId);
}
