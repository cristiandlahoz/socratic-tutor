package com.wornux.services.context;

import java.util.Comparator;
import java.util.List;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.authorization.TenantAccountRole;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.authorization.ScopeLevel;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.authorization.AccountPlatformRoleRepository;
import com.wornux.data.repositories.authorization.TenantAccountRoleRepository;
import com.wornux.security.permission.AppPermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContextDiscoveryService {

    private final AccountPlatformRoleRepository accountPlatformRoleRepository;
    private final TenantAccountRoleRepository tenantAccountRoleRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;

    public ContextDiscoveryService(
            AccountPlatformRoleRepository accountPlatformRoleRepository,
            TenantAccountRoleRepository tenantAccountRoleRepository,
            GroupClassMemberRepository groupClassMemberRepository) {
        this.accountPlatformRoleRepository = accountPlatformRoleRepository;
        this.tenantAccountRoleRepository = tenantAccountRoleRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<AvailableContextOption> discover(Account account) {
        var options = new java.util.ArrayList<AvailableContextOption>();
        if (isPlatformAccount(account)) {
            options.add(new AvailableContextOption(
                    ScopeLevel.PLATFORM,
                    null,
                    null,
                    "Plataforma",
                    "Administración global del sistema",
                    account.getEmail()));
        }

        findTenantAdminRoles(account).stream().findFirst().ifPresent(role -> {
            var tenant = role.getTenantAccount().getTenant();
            options.add(new AvailableContextOption(
                    ScopeLevel.TENANT,
                    tenant.getId(),
                    null,
                    tenant.getName(),
                    "Administración institucional",
                    account.getEmail()));
        });

        groupClassMemberRepository.findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(account.getId())
                .stream()
                .map(this::toClassOption)
                .forEach(options::add);

        return List.copyOf(options);
    }

    private boolean isPlatformAccount(Account account) {
        return accountPlatformRoleRepository.findByAccount_IdAndRole_ActiveTrue(account.getId())
                .stream()
                .anyMatch(role -> hasPermission(role.getRole().getPermissions(), AppPermission.TENANT_VIEW));
    }

    private List<TenantAccountRole> findTenantAdminRoles(Account account) {
        return tenantAccountRoleRepository.findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(account.getId())
                .stream()
                .filter(role -> role.getRole().isActive())
                .filter(role -> hasPermission(role.getRole().getPermissions(), AppPermission.GROUP_CLASS_CREATE))
                .sorted(Comparator.comparing(role -> role.getTenantAccount().getJoinedAt()))
                .toList();
    }

    private boolean hasPermission(String[] permissions, AppPermission permission) {
        return java.util.Arrays.asList(permissions).contains(permission.code());
    }

    private AvailableContextOption toClassOption(GroupClassMember member) {
        var groupClass = member.getGroupClass();
        return new AvailableContextOption(
                ScopeLevel.GROUP_CLASS,
                groupClass.getTenant().getId(),
                groupClass.getId(),
                "%s · %s".formatted(groupClass.getCode(), groupClass.getName()),
                groupClass.getTenant().getName(),
                member.getMemberKind().name());
    }
}
