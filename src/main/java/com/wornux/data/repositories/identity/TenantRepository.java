package com.wornux.data.repositories.identity;

import java.util.UUID;

import com.wornux.data.entities.identity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {}
