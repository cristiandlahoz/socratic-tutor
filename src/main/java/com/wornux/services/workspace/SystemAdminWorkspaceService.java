package com.wornux.services.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.Tenant;
import com.wornux.data.entities.onboarding.InvitationTargetRole;
import com.wornux.data.repositories.authorization.AccountRoleRepository;
import com.wornux.data.repositories.authorization.TenantAccountRoleRepository;
import com.wornux.data.repositories.identity.TenantRepository;
import com.wornux.services.onboarding.InvitationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAdminWorkspaceService {

    private final AccountRoleRepository accountRoleRepository;
    private final TenantRepository tenantRepository;
    private final TenantAccountRoleRepository tenantAccountRoleRepository;
    private final InvitationService invitationService;

    public SystemAdminWorkspaceService(
            AccountRoleRepository accountRoleRepository,
            TenantRepository tenantRepository,
            TenantAccountRoleRepository tenantAccountRoleRepository,
            InvitationService invitationService) {
        this.accountRoleRepository = accountRoleRepository;
        this.tenantRepository = tenantRepository;
        this.tenantAccountRoleRepository = tenantAccountRoleRepository;
        this.invitationService = invitationService;
    }

    @Transactional(readOnly = true)
    public List<Tenant> listTenants() {
        return tenantRepository.findAll().stream().sorted(java.util.Comparator.comparing(Tenant::getName)).toList();
    }

    @Transactional
    public Tenant createTenant(Account account, String tenantName) {
        if (!isSystemAdmin(account)) {
            throw new SecurityException("Only a system admin can create tenants.");
        }
        var tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(tenantName.trim());
        tenant.setCreatedByAccount(account);
        tenant.setLocked(false);
        tenant.setCreatedAt(Instant.now());
        tenant.setUpdatedAt(Instant.now());
        return tenantRepository.save(tenant);
    }

    @Transactional(readOnly = true)
    public boolean hasTenantAdmin(UUID tenantId) {
        return !tenantAccountRoleRepository
                .findByTenantAccount_Tenant_IdAndRole_CodeAndTenantAccount_LockedFalse(tenantId, "TENANT_ADMIN")
                .isEmpty();
    }

    @Transactional
    public void inviteTenantAdmin(Account account, UUID tenantId, String email) {
        if (!isSystemAdmin(account)) {
            throw new SecurityException("Only a system admin can invite tenant admins.");
        }
        invitationService
                .createInvitation(InvitationTargetRole.TENANT_ADMIN, tenantId, null, email, account, null, null);
    }

    private boolean isSystemAdmin(Account account) {
        return accountRoleRepository.findByAccount_IdAndRole_CodeAndRole_ActiveTrue(account.getId(), "SYSTEM_ADMIN")
                .isPresent();
    }
}
