package com.wornux.services.workspace;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.authorization.TenantAccountRole;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.AccountContextPreference;
import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.data.entities.identity.Tenant;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.authorization.AccountPlatformRoleRepository;
import com.wornux.data.repositories.authorization.TenantAccountRoleRepository;
import com.wornux.data.repositories.identity.AccountContextPreferenceRepository;
import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.security.AuthenticatedUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceRoutingService {

    private final AuthenticatedAccountService authenticatedAccountService;
    private final AuthenticatedUserContext authenticatedUserContext;
    private final AccountRepository accountRepository;
    private final TenantAccountRepository tenantAccountRepository;
    private final AccountContextPreferenceRepository accountContextPreferenceRepository;
    private final AccountPlatformRoleRepository accountPlatformRoleRepository;
    private final TenantAccountRoleRepository tenantAccountRoleRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;

    public WorkspaceRoutingService(
            AuthenticatedAccountService authenticatedAccountService,
            AuthenticatedUserContext authenticatedUserContext,
            AccountRepository accountRepository,
            TenantAccountRepository tenantAccountRepository,
            AccountContextPreferenceRepository accountContextPreferenceRepository,
            AccountPlatformRoleRepository accountPlatformRoleRepository,
            TenantAccountRoleRepository tenantAccountRoleRepository,
            GroupClassMemberRepository groupClassMemberRepository) {
        this.authenticatedAccountService = authenticatedAccountService;
        this.authenticatedUserContext = authenticatedUserContext;
        this.accountRepository = accountRepository;
        this.tenantAccountRepository = tenantAccountRepository;
        this.accountContextPreferenceRepository = accountContextPreferenceRepository;
        this.accountPlatformRoleRepository = accountPlatformRoleRepository;
        this.tenantAccountRoleRepository = tenantAccountRoleRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
    }

    @Transactional(readOnly = true)
    public WorkspaceDecision resolveCurrentUserDestination() {
        return authenticatedAccountService.currentAccount()
                .map(this::resolveForAccount)
                .orElse(new WorkspaceDecision(WorkspaceDestination.NO_ACCESS, null, null));
    }

    @Transactional
    public WorkspaceDecision resolveForAccount(Account account) {
        if (hasGlobalRole(account, "SYSTEM_ADMIN")) {
            setContext(account, ContextLevel.PLATFORM, null, null);
            return new WorkspaceDecision(WorkspaceDestination.SYSTEM_ADMIN, null, null);
        }

        var tenantAdmin = findTenantAdminRoles(account).stream().findFirst();
        if (tenantAdmin.isPresent()) {
            var tenantAccount = tenantAdmin.get().getTenantAccount();
            setContext(account, ContextLevel.TENANT, tenantAccount.getTenant(), null);
            return new WorkspaceDecision(WorkspaceDestination.TENANT_ADMIN, tenantAccount.getId(), null);
        }

        var professorMember = findActiveMembers(account, GroupClassMemberKind.PROFESSOR).stream().findFirst();
        if (professorMember.isPresent()) {
            setClassContext(account, professorMember.get());
            return new WorkspaceDecision(WorkspaceDestination.PROFESSOR,
                    professorMember.get().getTenantAccount().getId(),
                    professorMember.get().getId());
        }

        var studentMember = findActiveMembers(account, GroupClassMemberKind.STUDENT).stream().findFirst();
        if (studentMember.isPresent()) {
            setClassContext(account, studentMember.get());
            return new WorkspaceDecision(WorkspaceDestination.STUDENT,
                    studentMember.get().getTenantAccount().getId(),
                    studentMember.get().getId());
        }

        return new WorkspaceDecision(WorkspaceDestination.NO_ACCESS, null, null);
    }

    @Transactional(readOnly = true)
    public boolean canAccessWorkspace(Account account, WorkspaceDestination destination) {
        return switch (destination) {
            case SYSTEM_ADMIN -> hasGlobalRole(account, "SYSTEM_ADMIN");
            case TENANT_ADMIN -> !findTenantAdminRoles(account).isEmpty();
            case PROFESSOR -> !findActiveMembers(account, GroupClassMemberKind.PROFESSOR).isEmpty();
            case STUDENT -> !findActiveMembers(account, GroupClassMemberKind.STUDENT).isEmpty();
            case NO_ACCESS -> true;
        };
    }

    @Transactional
    public boolean prepareWorkspaceAccess(Account account, WorkspaceDestination destination) {
        return switch (destination) {
            case SYSTEM_ADMIN -> hasGlobalRole(account, "SYSTEM_ADMIN");
            case TENANT_ADMIN -> prepareTenantAdminAccess(account);
            case PROFESSOR -> prepareGroupClassAccess(account, GroupClassMemberKind.PROFESSOR);
            case STUDENT -> prepareGroupClassAccess(account, GroupClassMemberKind.STUDENT);
            case NO_ACCESS -> true;
        };
    }

    @Transactional(readOnly = true)
    public List<AccessibleTenant> listAccessibleTenants(Account account) {
        return tenantAccountRoleRepository.findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(account.getId())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(TenantAccountRole::getTenantAccount))
                .entrySet()
                .stream()
                .map(entry -> new AccessibleTenant(entry.getKey().getTenant().getId(),
                        entry.getKey().getId(),
                        entry.getKey().getTenant().getName(),
                        entry.getValue().stream().map(role -> role.getRole().getCode()).sorted().toList()))
                .sorted(Comparator.comparing(AccessibleTenant::tenantName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccessibleClass> listAccessibleClasses(Account account, GroupClassMemberKind memberKind) {
        return groupClassMemberRepository
                .findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(account.getId())
                .stream()
                .filter(member -> member.getMemberKind() == memberKind)
                .map(member -> new AccessibleClass(member.getGroupClass().getId(),
                        member.getId(),
                        member.getTenantAccount().getId(),
                        member.getGroupClass().getTenant().getName(),
                        member.getGroupClass().getCode(),
                        member.getGroupClass().getName(),
                        member.getMemberKind()))
                .toList();
    }

    @Transactional
    public void switchTenant(Account account, UUID tenantAccountId) {
        var tenantAccount = tenantAccountRepository.findByIdAndAccount_Id(tenantAccountId, account.getId())
                .orElseThrow(() -> new SecurityException("The tenant context is not available for this account."));
        setContext(account, ContextLevel.TENANT, tenantAccount.getTenant(), null);
        authenticatedUserContext.refreshCurrentAuthentication(account.getId());
    }

    @Transactional
    public void switchGroupClass(Account account, UUID groupClassMemberId) {
        var membership = groupClassMemberRepository.findById(groupClassMemberId)
                .orElseThrow(() -> new SecurityException("The class context is not available for this account."));
        if (membership.isLocked() || !membership.getTenantAccount().getAccount().getId().equals(account.getId())) {
            throw new SecurityException("The class context is not available for this account.");
        }
        setClassContext(account, membership);
        authenticatedUserContext.refreshCurrentAuthentication(account.getId());
    }

    @Transactional(readOnly = true)
    public boolean canAccessGroupClass(Account account, UUID groupClassId, GroupClassMemberKind requiredKind) {
        return groupClassMemberRepository
                .findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(account.getId())
                .stream()
                .anyMatch(member -> member.getGroupClass().getId().equals(groupClassId)
                        && (requiredKind == null || member.getMemberKind() == requiredKind));
    }

    @Transactional(readOnly = true)
    public Optional<GroupClassMember> currentClassMembership(Account account, GroupClassMemberKind requiredKind) {
        return accountContextPreferenceRepository.findById(account.getId())
                .filter(preference -> preference.getGroupClass() != null)
                .flatMap(preference -> requiredKind == null
                        ? groupClassMemberRepository.findByGroupClass_IdAndTenantAccount_Account_IdAndLockedFalse(
                                preference.getGroupClass().getId(), account.getId())
                        : groupClassMemberRepository
                                .findByGroupClass_IdAndTenantAccount_Account_IdAndMemberKindAndLockedFalse(
                                        preference.getGroupClass().getId(), account.getId(), requiredKind));
    }

    private boolean prepareTenantAdminAccess(Account account) {
        var tenantAdminRoles = findTenantAdminRoles(account);
        if (tenantAdminRoles.isEmpty()) {
            return false;
        }
        var currentTenant = accountContextPreferenceRepository.findById(account.getId())
                .map(AccountContextPreference::getTenant);
        if (currentTenant.isPresent()
                && tenantAdminRoles.stream().anyMatch(role -> role.getTenantAccount().getTenant().getId()
                        .equals(currentTenant.get().getId()))) {
            return true;
        }
        var tenantAccount = tenantAdminRoles.getFirst().getTenantAccount();
        setContext(account, ContextLevel.TENANT, tenantAccount.getTenant(), null);
        return true;
    }

    private boolean prepareGroupClassAccess(Account account, GroupClassMemberKind requiredKind) {
        var activeMembers = findActiveMembers(account, requiredKind);
        if (activeMembers.isEmpty()) {
            return false;
        }
        var currentMembership = currentClassMembership(account, requiredKind);
        if (currentMembership.isPresent()) {
            return true;
        }
        setClassContext(account, activeMembers.getFirst());
        return true;
    }

    private boolean hasGlobalRole(Account account, String roleCode) {
        return accountPlatformRoleRepository.findByAccount_IdAndRole_CodeAndRole_ActiveTrue(account.getId(), roleCode).isPresent();
    }

    private List<TenantAccountRole> findTenantAdminRoles(Account account) {
        return tenantAccountRoleRepository.findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(account.getId())
                .stream()
                .filter(role -> "TENANT_ADMIN".equals(role.getRole().getCode()))
                .sorted(Comparator.comparing(role -> role.getTenantAccount().getJoinedAt()))
                .toList();
    }

    private List<GroupClassMember> findActiveMembers(Account account, GroupClassMemberKind requiredKind) {
        return groupClassMemberRepository
                .findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(account.getId())
                .stream()
                .filter(member -> member.getMemberKind() == requiredKind)
                .toList();
    }

    private void setClassContext(Account account, GroupClassMember member) {
        setContext(account, ContextLevel.GROUP_CLASS, member.getGroupClass().getTenant(), member.getGroupClass());
    }

    private void setContext(Account account, ContextLevel level, Tenant tenant, GroupClass groupClass) {
        var preference = accountContextPreferenceRepository.findById(account.getId()).orElseGet(() -> {
            var created = new AccountContextPreference();
            created.setAccount(account);
            return created;
        });
        preference.setContextLevel(level);
        preference.setTenant(tenant);
        preference.setGroupClass(groupClass);
        preference.setUpdatedAt(Instant.now());
        accountContextPreferenceRepository.save(preference);
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);
    }
}
