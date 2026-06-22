package com.wornux.data.repositories.authorization;

import com.wornux.data.entities.authorization.TenantAccountRole;
import com.wornux.data.entities.authorization.TenantAccountRoleId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantAccountRoleRepository extends JpaRepository<TenantAccountRole, TenantAccountRoleId> {
    List<TenantAccountRole> findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(UUID accountId);

    List<TenantAccountRole> findByTenantAccount_Tenant_IdAndRole_CodeAndTenantAccount_LockedFalse(UUID tenantId, String roleCode);

    Optional<TenantAccountRole> findByTenantAccount_IdAndRole_Code(UUID tenantAccountId, String roleCode);
}
