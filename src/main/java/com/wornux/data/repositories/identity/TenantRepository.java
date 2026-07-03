package com.wornux.data.repositories.identity;

import java.util.UUID;

import com.wornux.data.entities.identity.Tenant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    @Override
    @EntityGraph(attributePaths = "roleNamespace")
    java.util.Optional<Tenant> findById(UUID id);
}
