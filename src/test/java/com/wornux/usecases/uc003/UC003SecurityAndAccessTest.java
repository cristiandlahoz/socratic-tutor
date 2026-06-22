package com.wornux.usecases.uc003;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wornux.config.SecurityConfig;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.authorization.Role;
import com.wornux.data.entities.authorization.TenantAccountRole;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.Tenant;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.services.workspace.AccessibleTenant;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import jakarta.annotation.security.PermitAll;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UC003SecurityAndAccessTest {

    @Test
    void br01_securityStaysEnabledByDefault() {
        assertFalse(SecurityConfig.isSecurityDisabled(false));
        assertTrue(SecurityConfig.isSecurityDisabled(true));
    }

    @Test
    void br01_br51_onlyInvitationAcceptanceRemainsPublicAmongWorkspaceEntryRoutes() {
        assertTrue(com.wornux.ui.auth.InvitationAcceptView.class.isAnnotationPresent(PermitAll.class));
        assertFalse(com.wornux.ui.auth.LandingView.class.isAnnotationPresent(PermitAll.class));
        assertFalse(com.wornux.ui.auth.NoAccessView.class.isAnnotationPresent(PermitAll.class));
        assertFalse(com.wornux.ui.admin.SystemAdminWorkspaceView.class.isAnnotationPresent(PermitAll.class));
        assertFalse(com.wornux.ui.tenant.TenantAdminWorkspaceView.class.isAnnotationPresent(PermitAll.class));
        assertFalse(com.wornux.ui.professor.ProfessorWorkspaceView.class.isAnnotationPresent(PermitAll.class));
        assertFalse(com.wornux.ui.student.StudentWorkspaceView.class.isAnnotationPresent(PermitAll.class));
        assertFalse(com.wornux.ui.chat.ChatView.class.isAnnotationPresent(PermitAll.class));
        assertFalse(com.wornux.ui.evaluation.EvaluationView.class.isAnnotationPresent(PermitAll.class));
    }

    @Test
    void br01_br51_securityConfigPublicMatcherScopeIsNarrowedToInvitationAcceptance() throws Exception {
        var source = Files.readString(Path.of("src/main/java/com/wornux/config/SecurityConfig.java"));

        assertTrue(source.contains("\"/invitations/accept/**\""));
        assertFalse(source.contains("\"/register/**\""));
        assertFalse(source.contains("\"/mailpit/**\""));
    }

    @Test
    void br01_loginFirstContractIsProtectedAsFarAsRepoStackAllows() {
        assertTrue(com.wornux.ui.auth.LoginView.class.isAnnotationPresent(com.vaadin.flow.server.auth.AnonymousAllowed.class));
        assertFalse(com.wornux.ui.auth.LandingView.class.isAnnotationPresent(com.vaadin.flow.server.auth.AnonymousAllowed.class));
        assertFalse(com.wornux.ui.MainLayout.class.isAnnotationPresent(com.vaadin.flow.server.auth.AnonymousAllowed.class));
    }

    @Test
    void br03_br04_br05_br06_multiRoleUsersKeepDeterministicDefaultButCanPrepareAlternateWorkspaceAccess() {
        var authenticatedAccountService = mock(com.wornux.services.security.AuthenticatedAccountService.class);
        var accountRepository = mock(com.wornux.data.repositories.identity.AccountRepository.class);
        var tenantAccountRepository = mock(com.wornux.data.repositories.identity.TenantAccountRepository.class);
        var tenantAccountRoleRepository = mock(com.wornux.data.repositories.authorization.TenantAccountRoleRepository.class);
        var groupClassMemberRepository = mock(com.wornux.data.repositories.academic.GroupClassMemberRepository.class);

        var account = account("multirole@test.local");
        var tenantAccount = tenantAccount(account, tenant("Algorithms University"));
        var tenantAdminRole = new TenantAccountRole();
        tenantAdminRole.setRole(role("TENANT_ADMIN"));
        tenantAdminRole.setTenantAccount(tenantAccount);
        tenantAdminRole.setAssignedAt(Instant.now());
        when(tenantAccountRoleRepository.findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(account.getId()))
                .thenReturn(List.of(tenantAdminRole));

        var professorMembership = membership(account, tenantAccount.getTenant(), GroupClassMemberRole.PROFESSOR);
        when(groupClassMemberRepository.findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(account.getId()))
                .thenReturn(List.of(professorMembership));
        when(groupClassMemberRepository.findById(professorMembership.getId())).thenReturn(java.util.Optional.of(professorMembership));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var routingService = new WorkspaceRoutingService(
                authenticatedAccountService,
                accountRepository,
                tenantAccountRepository,
                tenantAccountRoleRepository,
                groupClassMemberRepository);

        assertEquals(WorkspaceDestination.TENANT_ADMIN, routingService.resolveForAccount(account).destination());
        assertTrue(routingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR));
        assertEquals(professorMembership.getId(), account.getLastGroupClassMember().getId());
    }

    @Test
    void br04_firstRenderPreservesBackendSelectedTenantAdminContext() {
        var preferredTenant = new AccessibleTenant(UUID.randomUUID(), UUID.randomUUID(), "Zulu Tenant", List.of("TENANT_ADMIN"));
        var alphabeticTenant = new AccessibleTenant(UUID.randomUUID(), UUID.randomUUID(), "Alpha Tenant", List.of("TENANT_ADMIN"));
        var account = account("tenant-admin@test.local");
        var lastTenantAccount = tenantAccount(account, tenant("Zulu Tenant"));
        lastTenantAccount.setId(preferredTenant.tenantAccountId());
        account.setLastTenantAccount(lastTenantAccount);

        var selected = com.wornux.ui.tenant.TenantAdminWorkspaceView.determineSelectedTenant(
                List.of(alphabeticTenant, preferredTenant),
                null,
                account);

        assertEquals(preferredTenant, selected);
    }

    private static Account account(String email) {
        var account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail(email);
        account.setUsername(email.substring(0, email.indexOf('@')));
        account.setFirstName("Test");
        account.setLastName("User");
        account.setPasswordHash("ignored");
        account.setSystemAdmin(false);
        account.setLocked(false);
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        return account;
    }

    private static Tenant tenant(String name) {
        var tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(name);
        tenant.setLocked(false);
        tenant.setCreatedAt(Instant.now());
        tenant.setUpdatedAt(Instant.now());
        return tenant;
    }

    private static TenantAccount tenantAccount(Account account, Tenant tenant) {
        var tenantAccount = new TenantAccount();
        tenantAccount.setId(UUID.randomUUID());
        tenantAccount.setAccount(account);
        tenantAccount.setTenant(tenant);
        tenantAccount.setLocked(false);
        tenantAccount.setJoinedAt(Instant.now());
        tenantAccount.setUpdatedAt(Instant.now());
        return tenantAccount;
    }

    private static Role role(String code) {
        var role = new Role();
        role.setId(Math.abs((long) code.hashCode()));
        role.setCode(code);
        role.setName(code);
        role.setDescription(code);
        role.setActive(true);
        role.setAssignable(true);
        role.setPriority(10);
        role.setCreatedAt(Instant.now());
        role.setUpdatedAt(Instant.now());
        return role;
    }

    private static com.wornux.data.entities.academic.GroupClass groupClass(Tenant tenant) {
        var groupClass = new com.wornux.data.entities.academic.GroupClass();
        groupClass.setId(UUID.randomUUID());
        groupClass.setTenant(tenant);
        groupClass.setCode("ALG-101-A");
        groupClass.setName("Algorithms 101");
        groupClass.setActive(true);
        groupClass.setCreatedAt(Instant.now());
        groupClass.setUpdatedAt(Instant.now());
        return groupClass;
    }

    private static com.wornux.data.entities.academic.GroupClassMember membership(
            Account account,
            Tenant tenant,
            GroupClassMemberRole role) {
        var membership = new com.wornux.data.entities.academic.GroupClassMember();
        membership.setId(UUID.randomUUID());
        membership.setTenantAccount(tenantAccount(account, tenant));
        membership.setGroupClass(groupClass(tenant));
        membership.setRole(role);
        membership.setLocked(false);
        membership.setJoinedAt(Instant.now());
        membership.setUpdatedAt(Instant.now());
        return membership;
    }
}
