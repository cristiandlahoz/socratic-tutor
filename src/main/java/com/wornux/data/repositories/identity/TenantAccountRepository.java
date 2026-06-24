package com.wornux.data.repositories.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.identity.TenantAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantAccountRepository extends JpaRepository<TenantAccount, UUID> {
    Optional<TenantAccount> findByIdAndAccount_Id(UUID tenantAccountId, UUID accountId);

    Optional<TenantAccount> findByTenant_IdAndAccount_Id(UUID tenantId, UUID accountId);

    List<TenantAccount> findByAccount_IdAndLockedFalse(UUID accountId);
}
