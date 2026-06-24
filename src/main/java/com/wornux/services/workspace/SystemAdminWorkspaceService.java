package com.wornux.services.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.Tenant;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.onboarding.InvitationTargetRole;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import com.wornux.data.repositories.identity.TenantRepository;
import com.wornux.services.onboarding.InvitationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAdminWorkspaceService {

    private final TenantRepository tenantRepository;
    private final TenantAccountRepository tenantAccountRepository;
    private final InvitationService invitationService;

    public SystemAdminWorkspaceService(
            TenantRepository tenantRepository,
            TenantAccountRepository tenantAccountRepository,
            InvitationService invitationService) {
        this.tenantRepository = tenantRepository;
        this.tenantAccountRepository = tenantAccountRepository;
        this.invitationService = invitationService;
    }

    @Transactional(readOnly = true)
    public List<Tenant> listTenants() {
        return tenantRepository.findAll().stream().sorted(java.util.Comparator.comparing(Tenant::getName)).toList();
    }

    @Transactional
    public Tenant createTenant(Account account, String tenantName) {
        if (!account.isSystemAdmin()) {
            throw new SecurityException("Only a system admin can create tenants.");
        }
        var tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(tenantName.trim());
        tenant.setLocked(false);
        tenant.setCreatedAt(Instant.now());
        tenant.setUpdatedAt(Instant.now());
        tenant = tenantRepository.save(tenant);
        var persistedTenant = tenant;

        var tenantAccount =
                tenantAccountRepository.findByTenant_IdAndAccount_Id(persistedTenant.getId(), account.getId())
                        .orElseGet(() -> {
                            var created = new TenantAccount();
                            created.setId(UUID.randomUUID());
                            created.setTenant(persistedTenant);
                            created.setAccount(account);
                            created.setLocked(false);
                            created.setJoinedAt(Instant.now());
                            created.setUpdatedAt(Instant.now());
                            return tenantAccountRepository.save(created);
                        });
        tenant.setOwnerTenantAccount(tenantAccount);
        tenant.setUpdatedAt(Instant.now());
        return tenantRepository.save(tenant);
    }

    @Transactional
    public void inviteTenantAdmin(Account account, UUID tenantId, String email) {
        if (!account.isSystemAdmin()) {
            throw new SecurityException("Only a system admin can invite tenant admins.");
        }
        invitationService
                .createInvitation(InvitationTargetRole.TENANT_ADMIN, tenantId, null, email, account, null, null);
    }
}
