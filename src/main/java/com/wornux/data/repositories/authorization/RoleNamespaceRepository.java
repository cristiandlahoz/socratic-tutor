package com.wornux.data.repositories.authorization;

import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.authorization.RoleNamespace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RoleNamespaceRepository extends JpaRepository<RoleNamespace, UUID> {
    Optional<RoleNamespace> findByCode(String code);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RoleNamespace namespace set namespace.rbacVersion = namespace.rbacVersion + 1, namespace.updatedAt = CURRENT_TIMESTAMP where namespace.id = :namespaceId")
    int incrementRbacVersion(UUID namespaceId);
}
