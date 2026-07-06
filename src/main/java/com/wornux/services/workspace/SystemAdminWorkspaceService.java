package com.wornux.services.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.Tenant;
import com.wornux.data.entities.onboarding.InvitationTargetRole;
import com.wornux.data.repositories.authorization.AccountPlatformRoleRepository;
import com.wornux.data.repositories.authorization.TenantAccountRoleRepository;
import com.wornux.data.repositories.identity.TenantRepository;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.onboarding.InvitationService;
import com.wornux.services.security.RoleNamespaceService;
import com.wornux.services.security.RoleSeedService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAdminWorkspaceService {

    private final AccountPlatformRoleRepository accountPlatformRoleRepository;
    private final TenantRepository tenantRepository;
    private final TenantAccountRoleRepository tenantAccountRoleRepository;
    private final InvitationService invitationService;
    private final RoleNamespaceService roleNamespaceService;
    private final RoleSeedService roleSeedService;

    public SystemAdminWorkspaceService(
            AccountPlatformRoleRepository accountPlatformRoleRepository,
            TenantRepository tenantRepository,
            TenantAccountRoleRepository tenantAccountRoleRepository,
            InvitationService invitationService,
            RoleNamespaceService roleNamespaceService,
            RoleSeedService roleSeedService) {
        this.accountPlatformRoleRepository = accountPlatformRoleRepository;
        this.tenantRepository = tenantRepository;
        this.tenantAccountRoleRepository = tenantAccountRoleRepository;
        this.invitationService = invitationService;
        this.roleNamespaceService = roleNamespaceService;
        this.roleSeedService = roleSeedService;
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
        var namespace = roleNamespaceService.create("tenant:%s".formatted(tenant.getId()));
        tenant.setRoleNamespace(namespace);
        tenant.setName(tenantName.trim());
        tenant.setCreatedByAccount(account);
        tenant.setLocked(false);
        tenant.setCreatedAt(Instant.now());
        tenant.setUpdatedAt(Instant.now());
        var saved = tenantRepository.save(tenant);
        roleSeedService.seedTenantDefaultRoles(namespace);
        return saved;
    }

    @Transactional(readOnly = true)
    public boolean hasTenantAdmin(UUID tenantId) {
        return tenantAccountRoleRepository
                .findByTenantAccount_Tenant_IdAndTenantAccount_LockedFalseAndRole_ActiveTrue(tenantId)
                .stream()
                .anyMatch(role -> hasPermission(role.getRole().getPermissions(), AppPermission.GROUP_CLASS_CREATE));
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
        return accountPlatformRoleRepository.findByAccount_IdAndRole_ActiveTrue(account.getId())
                .stream()
                .anyMatch(role -> hasPermission(role.getRole().getPermissions(), AppPermission.TENANT_CREATE));
    }

    private boolean hasPermission(String[] permissions, AppPermission permission) {
        return java.util.Arrays.asList(permissions).contains(permission.code());
    }
}
