package com.wornux.services.context;

import java.util.Comparator;
import java.util.List;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.authorization.TenantAccountRole;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.authorization.AccountPlatformRoleRepository;
import com.wornux.data.repositories.authorization.TenantAccountRoleRepository;
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
            options.add(
                new AvailableContextOption(ContextLevel.PLATFORM,
                        null,
                        null,
                        "Plataforma",
                        "Administración global del sistema",
                        account.getEmail()));
        }

        findTenantAdminRoles(account).stream().findFirst().ifPresent(role -> {
            var tenant = role.getTenantAccount().getTenant();
            options.add(
                new AvailableContextOption(ContextLevel.TENANT,
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
                .anyMatch(
                    role -> role.getRole()
                            .getAssignmentLevel() == com.wornux.data.entities.authorization.RoleAssignmentLevel.PLATFORM);
    }

    private List<TenantAccountRole> findTenantAdminRoles(Account account) {
        return tenantAccountRoleRepository.findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(account.getId())
                .stream()
                .filter(
                    role -> role.getRole()
                            .getAssignmentLevel() == com.wornux.data.entities.authorization.RoleAssignmentLevel.TENANT)
                .sorted(Comparator.comparing(role -> role.getTenantAccount().getJoinedAt()))
                .toList();
    }

    private AvailableContextOption toClassOption(GroupClassMember member) {
        var groupClass = member.getGroupClass();
        return new AvailableContextOption(ContextLevel.GROUP_CLASS,
                groupClass.getTenant().getId(),
                groupClass.getId(),
                "%s · %s".formatted(groupClass.getCode(), groupClass.getName()),
                groupClass.getTenant().getName(),
                member.getMemberKind().name());
    }
}
