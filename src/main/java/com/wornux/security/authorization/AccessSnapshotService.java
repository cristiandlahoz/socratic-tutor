package com.wornux.security.authorization;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wornux.data.entities.authorization.Role;
import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.authorization.AccountPlatformRoleRepository;
import com.wornux.data.repositories.authorization.GroupClassMemberRoleRepository;
import com.wornux.data.repositories.authorization.PlatformSettingsRepository;
import com.wornux.data.repositories.authorization.TenantAccountRoleRepository;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import com.wornux.data.repositories.identity.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessSnapshotService {

    private final TenantAccountRepository tenantAccountRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;
    private final AccountPlatformRoleRepository accountPlatformRoleRepository;
    private final TenantAccountRoleRepository tenantAccountRoleRepository;
    private final GroupClassMemberRoleRepository groupClassMemberRoleRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    private final TenantRepository tenantRepository;
    private final Cache<SnapshotCacheKey, UserAccessSnapshot> cache;

    public AccessSnapshotService(
            TenantAccountRepository tenantAccountRepository,
            GroupClassMemberRepository groupClassMemberRepository,
            AccountPlatformRoleRepository accountPlatformRoleRepository,
            TenantAccountRoleRepository tenantAccountRoleRepository,
            GroupClassMemberRoleRepository groupClassMemberRoleRepository,
            PlatformSettingsRepository platformSettingsRepository,
            TenantRepository tenantRepository) {
        this.tenantAccountRepository = tenantAccountRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
        this.accountPlatformRoleRepository = accountPlatformRoleRepository;
        this.tenantAccountRoleRepository = tenantAccountRoleRepository;
        this.groupClassMemberRoleRepository = groupClassMemberRoleRepository;
        this.platformSettingsRepository = platformSettingsRepository;
        this.tenantRepository = tenantRepository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofMinutes(20))
                .build();
    }

    @Transactional(readOnly = true)
    public UserAccessSnapshot snapshot(UUID accountId, ActiveContext activeContext) {
        var namespace = resolveNamespace(activeContext);
        var key = new SnapshotCacheKey(
                accountId,
                activeContext.level(),
                activeContext.tenantId(),
                activeContext.groupClassId(),
                namespace.id(),
                namespace.version());
        return cache.get(key, ignored -> loadSnapshot(accountId, activeContext, namespace.version()));
    }

    public void invalidateNamespace(UUID roleNamespaceId) {
        cache.asMap().keySet().removeIf(key -> key.roleNamespaceId().equals(roleNamespaceId));
    }

    public void invalidateAccount(UUID accountId) {
        cache.asMap().keySet().removeIf(key -> key.accountId().equals(accountId));
    }

    public void invalidateContext(UUID accountId, ActiveContext activeContext) {
        cache.asMap().keySet().removeIf(key -> key.accountId().equals(accountId)
                && key.contextLevel() == activeContext.level()
                && equalsNullable(key.tenantId(), activeContext.tenantId())
                && equalsNullable(key.groupClassId(), activeContext.groupClassId()));
    }

    private NamespaceVersion resolveNamespace(ActiveContext activeContext) {
        if (activeContext.level() == ContextLevel.PLATFORM) {
            var settings = platformSettingsRepository.findById(Boolean.TRUE)
                    .orElseThrow(() -> new IllegalStateException("Platform settings are not initialized"));
            return new NamespaceVersion(settings.getRoleNamespace().getId(), settings.getRoleNamespace().getRbacVersion());
        }
        var tenant = tenantRepository.findById(activeContext.tenantId())
                .orElseThrow(() -> new IllegalStateException("Unknown tenant %s".formatted(activeContext.tenantId())));
        return new NamespaceVersion(tenant.getRoleNamespace().getId(), tenant.getRoleNamespace().getRbacVersion());
    }

    private UserAccessSnapshot loadSnapshot(UUID accountId, ActiveContext activeContext, long namespaceVersion) {
        if (activeContext.level() == ContextLevel.PLATFORM) {
            var roleCodes = new LinkedHashSet<String>();
            var permissionCodes = new LinkedHashSet<String>();
            accountPlatformRoleRepository.findByAccount_IdAndRole_ActiveTrue(accountId)
                    .forEach(assignment -> addRole(assignment.getRole(), roleCodes, permissionCodes));
            return new UserAccessSnapshot(
                    accountId, activeContext, null, null, null, null, null,
                    Set.copyOf(roleCodes), Set.copyOf(permissionCodes), namespaceVersion);
        }

        var tenantAccount = tenantAccountRepository.findByTenant_IdAndAccount_Id(activeContext.tenantId(), accountId)
                .orElseThrow(() -> new IllegalStateException("Account %s is not a tenant member of %s".formatted(accountId, activeContext.tenantId())));
        var roleCodes = new LinkedHashSet<String>();
        var permissionCodes = new LinkedHashSet<String>();
        tenantAccountRoleRepository.findByTenantAccount_IdAndRole_ActiveTrue(tenantAccount.getId())
                .forEach(assignment -> addRole(assignment.getRole(), roleCodes, permissionCodes));

        if (activeContext.level() == ContextLevel.TENANT) {
            return new UserAccessSnapshot(
                    accountId, activeContext, activeContext.tenantId(), tenantAccount.getId(), null, null, null,
                    Set.copyOf(roleCodes), Set.copyOf(permissionCodes), namespaceVersion);
        }

        var membership = groupClassMemberRepository
                .findByGroupClass_IdAndTenantAccount_Id(activeContext.groupClassId(), tenantAccount.getId())
                .filter(member -> !member.isLocked());
        membership.ifPresent(member -> groupClassMemberRoleRepository.findByGroupClassMember_Id(member.getId())
                .forEach(assignment -> addRole(assignment.getRole(), roleCodes, permissionCodes)));

        return new UserAccessSnapshot(
                accountId,
                activeContext,
                activeContext.tenantId(),
                tenantAccount.getId(),
                activeContext.groupClassId(),
                membership.map(member -> member.getId()).orElse(null),
                membership.map(member -> member.getMemberKind()).orElse(null),
                Set.copyOf(roleCodes),
                Set.copyOf(permissionCodes),
                namespaceVersion);
    }

    private void addRole(Role role, Set<String> roleCodes, Set<String> permissionCodes) {
        if (role == null || !role.isActive()) {
            return;
        }
        roleCodes.add(role.getCode());
        permissionCodes.addAll(Arrays.asList(role.getPermissions()));
    }

    private boolean equalsNullable(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private record NamespaceVersion(UUID id, long version) {
    }

    private record SnapshotCacheKey(
            UUID accountId,
            ContextLevel contextLevel,
            UUID tenantId,
            UUID groupClassId,
            UUID roleNamespaceId,
            long roleNamespaceVersion) {
    }
}
