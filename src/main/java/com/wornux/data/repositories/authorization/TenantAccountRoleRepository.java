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

    @EntityGraph(attributePaths = {"role", "tenantAccount", "tenantAccount.tenant"})
    List<TenantAccountRole> findByTenantAccount_Tenant_IdAndTenantAccount_LockedFalseAndRole_ActiveTrue(UUID tenantId);

    List<TenantAccountRole> findByTenantAccount_Tenant_IdAndTenantAccount_LockedFalseAndRole_Id(UUID tenantId, UUID roleId);

    long countByTenantAccount_Tenant_IdAndTenantAccount_LockedFalseAndRole_Id(UUID tenantId, UUID roleId);

    Optional<TenantAccountRole> findByTenantAccount_IdAndRole_Code(UUID tenantAccountId, String roleCode);
}
