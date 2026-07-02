package com.wornux.data.repositories.authorization;

import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.authorization.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);

    Optional<Role> findByRoleNamespace_IdAndCode(UUID roleNamespaceId, String code);
}
