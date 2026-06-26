package com.wornux.services.workspace;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.authorization.TenantAccountRole;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.authorization.AccountRoleRepository;
import com.wornux.data.repositories.authorization.TenantAccountRoleRepository;
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
    private final AccountRoleRepository accountRoleRepository;
    private final TenantAccountRoleRepository tenantAccountRoleRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;

    public WorkspaceRoutingService(
            AuthenticatedAccountService authenticatedAccountService,
            AuthenticatedUserContext authenticatedUserContext,
            AccountRepository accountRepository,
            TenantAccountRepository tenantAccountRepository,
            AccountRoleRepository accountRoleRepository,
            TenantAccountRoleRepository tenantAccountRoleRepository,
            GroupClassMemberRepository groupClassMemberRepository) {
        this.authenticatedAccountService = authenticatedAccountService;
        this.authenticatedUserContext = authenticatedUserContext;
        this.accountRepository = accountRepository;
        this.tenantAccountRepository = tenantAccountRepository;
        this.accountRoleRepository = accountRoleRepository;
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
            return new WorkspaceDecision(WorkspaceDestination.SYSTEM_ADMIN,
                    account.getLastTenantAccount() == null ? null : account.getLastTenantAccount().getId(),
                    null);
        }

        var tenantRoles =
                tenantAccountRoleRepository.findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(account.getId());
        var tenantAdmin = tenantRoles.stream()
                .filter(role -> "TENANT_ADMIN".equals(role.getRole().getCode()))
                .min(Comparator.comparing(role -> role.getTenantAccount().getJoinedAt()));
        if (tenantAdmin.isPresent()) {
            ensureLastTenantAccount(account, tenantAdmin.get());
            return new WorkspaceDecision(WorkspaceDestination.TENANT_ADMIN,
                    tenantAdmin.get().getTenantAccount().getId(),
                    null);
        }

        var activeMembers = groupClassMemberRepository
                .findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(account.getId());
        var professorMember =
                activeMembers.stream().filter(member -> member.getRole() == GroupClassMemberRole.PROFESSOR).findFirst();
        if (professorMember.isPresent()) {
            ensureLastClassContext(account, professorMember.get());
            return new WorkspaceDecision(WorkspaceDestination.PROFESSOR,
                    professorMember.get().getTenantAccount().getId(),
                    professorMember.get().getId());
        }

        var studentMember =
                activeMembers.stream().filter(member -> member.getRole() == GroupClassMemberRole.STUDENT).findFirst();
        if (studentMember.isPresent()) {
            ensureLastClassContext(account, studentMember.get());
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
            case PROFESSOR -> !findActiveMembers(account, GroupClassMemberRole.PROFESSOR).isEmpty();
            case STUDENT -> !findActiveMembers(account, GroupClassMemberRole.STUDENT).isEmpty();
            case NO_ACCESS -> true;
        };
    }

    @Transactional
    public boolean prepareWorkspaceAccess(Account account, WorkspaceDestination destination) {
        return switch (destination) {
            case SYSTEM_ADMIN -> hasGlobalRole(account, "SYSTEM_ADMIN");
            case TENANT_ADMIN -> prepareTenantAdminAccess(account);
            case PROFESSOR -> prepareGroupClassAccess(account, GroupClassMemberRole.PROFESSOR);
            case STUDENT -> prepareGroupClassAccess(account, GroupClassMemberRole.STUDENT);
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
                .map(
                    entry -> new AccessibleTenant(entry.getKey().getTenant().getId(),
                            entry.getKey().getId(),
                            entry.getKey().getTenant().getName(),
                            entry.getValue().stream().map(role -> role.getRole().getCode()).sorted().toList()))
                .sorted(Comparator.comparing(AccessibleTenant::tenantName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccessibleClass> listAccessibleClasses(Account account, GroupClassMemberRole role) {
        return groupClassMemberRepository
                .findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(account.getId())
                .stream()
                .filter(member -> member.getRole() == role)
                .map(
                    member -> new AccessibleClass(member.getGroupClass().getId(),
                            member.getId(),
                            member.getTenantAccount().getId(),
                            member.getGroupClass().getTenant().getName(),
                            member.getGroupClass().getCode(),
                            member.getGroupClass().getName(),
                            member.getRole()))
                .toList();
    }

    @Transactional
    public void switchTenant(Account account, UUID tenantAccountId) {
        var tenantAccount = tenantAccountRepository.findByIdAndAccount_Id(tenantAccountId, account.getId())
                .orElseThrow(() -> new SecurityException("The tenant context is not available for this account."));
        account.setLastTenantAccount(tenantAccount);
        accountRepository.save(account);
        authenticatedUserContext.refreshCurrentAuthentication(account.getId());
    }

    @Transactional
    public void switchGroupClass(Account account, UUID groupClassMemberId) {
        var membership = groupClassMemberRepository
                .findById(account.getLastGroupClassMember() == null ? groupClassMemberId : groupClassMemberId)
                .orElseThrow(() -> new SecurityException("The class context is not available for this account."));
        if (membership.isLocked() || !membership.getTenantAccount().getAccount().getId().equals(account.getId())) {
            throw new SecurityException("The class context is not available for this account.");
        }
        account.setLastTenantAccount(membership.getTenantAccount());
        account.setLastGroupClassMember(membership);
        accountRepository.save(account);
        authenticatedUserContext.refreshCurrentAuthentication(account.getId());
    }

    @Transactional(readOnly = true)
    public boolean canAccessGroupClass(Account account, UUID groupClassId, GroupClassMemberRole requiredRole) {
        return groupClassMemberRepository
                .findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(account.getId())
                .stream()
                .anyMatch(
                    member -> member.getGroupClass().getId().equals(groupClassId)
                            && (requiredRole == null || member.getRole() == requiredRole));
    }

    @Transactional(readOnly = true)
    public Optional<GroupClassMember> currentClassMembership(Account account, GroupClassMemberRole requiredRole) {
        var membership = account.getLastGroupClassMember();
        if (membership == null) {
            return Optional.empty();
        }
        return groupClassMemberRepository.findById(membership.getId())
                .filter(
                    found -> !found.isLocked()
                            && found.getTenantAccount().getAccount().getId().equals(account.getId())
                            && (requiredRole == null || found.getRole() == requiredRole));
    }

    private void ensureLastTenantAccount(Account account, TenantAccountRole tenantAdminRole) {
        if (account.getLastTenantAccount() != null
                && account.getLastTenantAccount().getId().equals(tenantAdminRole.getTenantAccount().getId())) {
            return;
        }
        account.setLastTenantAccount(tenantAdminRole.getTenantAccount());
        accountRepository.save(account);
        authenticatedUserContext.refreshCurrentAuthentication(account.getId());
    }

    private boolean prepareTenantAdminAccess(Account account) {
        var tenantAdminRoles = findTenantAdminRoles(account);
        if (tenantAdminRoles.isEmpty()) {
            return false;
        }
        var currentTenantAccount = account.getLastTenantAccount();
        if (currentTenantAccount != null
                && tenantAdminRoles.stream()
                        .anyMatch(role -> role.getTenantAccount().getId().equals(currentTenantAccount.getId()))) {
            return true;
        }
        ensureLastTenantAccount(account, tenantAdminRoles.getFirst());
        return true;
    }

    private boolean prepareGroupClassAccess(Account account, GroupClassMemberRole requiredRole) {
        var activeMembers = findActiveMembers(account, requiredRole);
        if (activeMembers.isEmpty()) {
            return false;
        }
        var currentMembership = currentClassMembership(account, requiredRole);
        if (currentMembership.isPresent()) {
            return true;
        }
        ensureLastClassContext(account, activeMembers.getFirst());
        return true;
    }

    private boolean hasGlobalRole(Account account, String roleCode) {
        return accountRoleRepository.findByAccount_IdAndRole_CodeAndRole_ActiveTrue(account.getId(), roleCode).isPresent();
    }

    private List<TenantAccountRole> findTenantAdminRoles(Account account) {
        return tenantAccountRoleRepository.findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(account.getId())
                .stream()
                .filter(role -> "TENANT_ADMIN".equals(role.getRole().getCode()))
                .sorted(Comparator.comparing(role -> role.getTenantAccount().getJoinedAt()))
                .toList();
    }

    private List<GroupClassMember> findActiveMembers(Account account, GroupClassMemberRole requiredRole) {
        return groupClassMemberRepository
                .findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(account.getId())
                .stream()
                .filter(member -> member.getRole() == requiredRole)
                .toList();
    }

    private void ensureLastClassContext(Account account, GroupClassMember member) {
        if (account.getLastGroupClassMember() != null
                && account.getLastTenantAccount() != null
                && account.getLastGroupClassMember().getId().equals(member.getId())
                && account.getLastTenantAccount().getId().equals(member.getTenantAccount().getId())) {
            return;
        }
        account.setLastTenantAccount(member.getTenantAccount());
        account.setLastGroupClassMember(member);
        accountRepository.save(account);
        authenticatedUserContext.refreshCurrentAuthentication(account.getId());
    }
}
