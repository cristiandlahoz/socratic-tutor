package com.wornux.data.repositories.authorization;

import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.authorization.RoleNamespace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleNamespaceRepository extends JpaRepository<RoleNamespace, UUID> {
    Optional<RoleNamespace> findByCode(String code);
}
