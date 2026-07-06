package com.wornux.data.repositories.authorization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.authorization.TenantAccountRole;
import com.wornux.data.entities.authorization.TenantAccountRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantAccountRoleRepository extends JpaRepository<TenantAccountRole, TenantAccountRoleId> {
    @EntityGraph(attributePaths = {
            "role",
            "role.roleNamespace",
            "tenantAccount",
            "tenantAccount.tenant",
            "tenantAccount.tenant.roleNamespace" })
    List<TenantAccountRole> findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(UUID accountId);

    @EntityGraph(attributePaths = {
            "role",
            "role.roleNamespace",
            "tenantAccount",
            "tenantAccount.tenant",
            "tenantAccount.tenant.roleNamespace" })
    List<TenantAccountRole> findByTenantAccount_IdAndRole_ActiveTrue(UUID tenantAccountId);

    List<TenantAccountRole> findByTenantAccount_Tenant_IdAndRole_CodeAndTenantAccount_LockedFalse(
            UUID tenantId,
            String roleCode);

    Optional<TenantAccountRole> findByTenantAccount_IdAndRole_Code(UUID tenantAccountId, String roleCode);
}
