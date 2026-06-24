package com.wornux.usecases.uc003;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.config.SocraticEmailProperties;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.authorization.Role;
import com.wornux.data.entities.authorization.TenantAccountRole;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.Tenant;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.onboarding.Invitation;
import com.wornux.data.entities.onboarding.InvitationStatus;
import com.wornux.data.entities.onboarding.InvitationTargetRole;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.academic.GroupClassRepository;
import com.wornux.data.repositories.authorization.RoleRepository;
import com.wornux.data.repositories.authorization.TenantAccountRoleRepository;
import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import com.wornux.data.repositories.identity.TenantRepository;
import com.wornux.data.repositories.onboarding.InvitationRepository;
import com.wornux.services.onboarding.InvitationEmailService;
import com.wornux.services.onboarding.InvitationService;
import com.wornux.services.onboarding.InvitationStateException;
import com.wornux.services.onboarding.InvitationTokenService;
import com.wornux.services.onboarding.OnboardingSessionContext;
import com.wornux.services.onboarding.UsernameGenerator;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.workspace.ProfessorWorkspaceService;
import com.wornux.services.workspace.WorkspaceDecision;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UC003OnboardingAndWorkspaceTest {

    @Mock
    private InvitationRepository invitationRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantAccountRepository tenantAccountRepository;
    @Mock
    private GroupClassRepository groupClassRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private TenantAccountRoleRepository tenantAccountRoleRepository;
    @Mock
    private GroupClassMemberRepository groupClassMemberRepository;
    @Mock
    private InvitationEmailService invitationEmailService;
    @Mock
    private AuthenticatedAccountService authenticatedAccountService;
    @Mock
    private WorkspaceRoutingService workspaceRoutingService;

    private InvitationTokenService invitationTokenService;
    private OnboardingSessionContext onboardingSessionContext;
    private UsernameGenerator usernameGenerator;
    private InvitationService invitationService;

    @BeforeEach
    void setUp() {
        invitationTokenService = spy(new InvitationTokenService());
        onboardingSessionContext = new OnboardingSessionContext();
        usernameGenerator = new UsernameGenerator(accountRepository);
        var emailProperties = new SocraticEmailProperties();
        emailProperties.setInvitationExpiration(Duration.ofHours(72));
        emailProperties.setInvitationBaseUrl("http://localhost:8080");
        invitationService = new InvitationService(emailProperties,
                invitationRepository,
                accountRepository,
                tenantRepository,
                tenantAccountRepository,
                groupClassRepository,
                roleRepository,
                tenantAccountRoleRepository,
                groupClassMemberRepository,
                invitationTokenService,
                invitationEmailService,
                onboardingSessionContext,
                usernameGenerator,
                new BCryptPasswordEncoder(),
                authenticatedAccountService,
                workspaceRoutingService);
    }

    @Test
    void br11_br41_br42_invitationsPersistOnlyTokenHashes() {
        var tenant = tenant("Algorithms University");
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn("raw-token").when(invitationTokenService).generateRawToken();
        doReturn("hashed-token").when(invitationTokenService).hash("raw-token");

        invitationService.createInvitation(
            InvitationTargetRole.TENANT_ADMIN,
            tenant.getId(),
            null,
            "tenant-admin@test.local",
            account("admin@test.local"),
            null,
            null);

        var captor = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).save(captor.capture());
        assertEquals("hashed-token", captor.getValue().getTokenHash());
        assertNotEquals("raw-token", captor.getValue().getTokenHash());
    }

    @Test
    void br31_br32_acceptedAndExpiredInvitationsAreProtected() {
        var accepted = pendingInvitation(InvitationTargetRole.STUDENT, InvitationStatus.ACCEPTED);
        when(invitationRepository.findByTokenHash("accepted-hash")).thenReturn(Optional.of(accepted));
        doReturn("accepted-hash").when(invitationTokenService).hash("accepted-token");

        assertThrows(InvitationStateException.class, () -> invitationService.prepareOnboarding("accepted-token"));

        var expired = pendingInvitation(InvitationTargetRole.STUDENT, InvitationStatus.PENDING);
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        when(invitationRepository.findByTokenHash("expired-hash")).thenReturn(Optional.of(expired));
        doReturn("expired-hash").when(invitationTokenService).hash("expired-token");

        assertThrows(InvitationStateException.class, () -> invitationService.prepareOnboarding("expired-token"));
        verify(invitationRepository, atLeastOnce()).save(expired);
        assertEquals(InvitationStatus.EXPIRED, expired.getStatus());
    }

    @Test
    void br57_br58_br59_br60_br61_invitedRegistrationUsesReadOnlyEmailPasswordEncoderAndUniqueUsername() {
        var invitation = pendingInvitation(InvitationTargetRole.STUDENT, InvitationStatus.PENDING);
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        onboardingSessionContext.setInvitationId(1L);
        onboardingSessionContext.setInvitationId(invitation.getId());
        invitation.setInvitedEmail("manu.el@test.local");
        onboardingSessionContext.setInvitedEmail("manu.el@test.local");
        onboardingSessionContext.setAccountAlreadyExists(false);
        when(accountRepository.findByEmail("manu.el@test.local")).thenReturn(Optional.empty());
        when(accountRepository.existsByUsername("manuel")).thenReturn(true);
        when(accountRepository.existsByUsername("manuel2")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var account = invitationService.registerInvitedAccount("Manuel", "Perez", "secret-123", "secret-123");

        assertEquals("manu.el@test.local", account.getEmail());
        assertEquals("manuel2", account.getUsername());
        assertNotEquals("secret-123", account.getPasswordHash());
        assertTrue(new BCryptPasswordEncoder().matches("secret-123", account.getPasswordHash()));
    }

    @Test
    void br31_br32_registrationRevalidatesInvitationStateBeforeAccountCreation() {
        var invitation = pendingInvitation(InvitationTargetRole.STUDENT, InvitationStatus.REVOKED);
        onboardingSessionContext.setInvitationId(invitation.getId());
        onboardingSessionContext.setInvitedEmail(invitation.getInvitedEmail());
        onboardingSessionContext.setAccountAlreadyExists(false);
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThrows(
            InvitationStateException.class,
            () -> invitationService.registerInvitedAccount("Manuel", "Perez", "secret-123", "secret-123"));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void br31_br32_finalAcceptanceRevalidatesInvitationStateBeforeMembershipCreation() {
        var invitation = pendingInvitation(InvitationTargetRole.PROFESSOR, InvitationStatus.REVOKED);
        var account = account(invitation.getInvitedEmail());
        onboardingSessionContext.setInvitationId(invitation.getId());
        onboardingSessionContext.setInvitedEmail(invitation.getInvitedEmail());
        when(authenticatedAccountService.requireCurrentAccount()).thenReturn(account);
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThrows(
            InvitationStateException.class,
            () -> invitationService.completePendingInvitationForCurrentAccount());
        verify(tenantAccountRepository, never()).save(any(TenantAccount.class));
        verify(groupClassMemberRepository, never()).save(any(GroupClassMember.class));
    }

    @Test
    void br62_prepareOnboardingMarksExistingAccountAsLoginOnly() {
        var invitation = pendingInvitation(InvitationTargetRole.PROFESSOR, InvitationStatus.PENDING);
        when(invitationRepository.findByTokenHash("existing-hash")).thenReturn(Optional.of(invitation));
        when(accountRepository.findByEmail(invitation.getInvitedEmail()))
                .thenReturn(Optional.of(account(invitation.getInvitedEmail())));
        doReturn("existing-hash").when(invitationTokenService).hash("existing-token");

        var onboarding = invitationService.prepareOnboarding("existing-token");

        assertTrue(onboarding.accountAlreadyExists());
        assertTrue(onboardingSessionContext.isAccountAlreadyExists());
    }

    @Test
    void br63_existingAccountAcceptanceRequiresMatchingEmail() {
        onboardingSessionContext.setInvitationId(1L);
        onboardingSessionContext.setInvitedEmail("invited@test.local");
        when(authenticatedAccountService.requireCurrentAccount()).thenReturn(account("other@test.local"));

        assertThrows(
            InvitationStateException.class,
            () -> invitationService.completePendingInvitationForCurrentAccount());
    }

    @Test
    void mainFlow_tenantAdminAcceptanceCreatesTenantAccountAndRole() {
        var invitation = pendingInvitation(InvitationTargetRole.TENANT_ADMIN, InvitationStatus.PENDING);
        var account = account(invitation.getInvitedEmail());
        onboardingSessionContext.setInvitationId(invitation.getId());
        onboardingSessionContext.setInvitedEmail(invitation.getInvitedEmail());
        when(authenticatedAccountService.requireCurrentAccount()).thenReturn(account);
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(tenantAccountRepository.findByTenant_IdAndAccount_Id(invitation.getTenant().getId(), account.getId()))
                .thenReturn(Optional.empty());
        when(tenantAccountRepository.save(any(TenantAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByCode("TENANT_ADMIN")).thenReturn(Optional.of(role("TENANT_ADMIN")));
        when(tenantAccountRoleRepository.findByTenantAccount_IdAndRole_Code(any(), eq("TENANT_ADMIN")))
                .thenReturn(Optional.empty());
        when(tenantAccountRoleRepository.save(any(TenantAccountRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workspaceRoutingService.resolveForAccount(account))
                .thenReturn(new WorkspaceDecision(WorkspaceDestination.TENANT_ADMIN, UUID.randomUUID(), null));

        var decision = invitationService.completePendingInvitationForCurrentAccount();

        assertEquals(WorkspaceDestination.TENANT_ADMIN, decision.destination());
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
        assertNotNull(account.getLastTenantAccount());
        verify(groupClassMemberRepository, never()).save(any(GroupClassMember.class));
    }

    @Test
    void mainFlow_professorAndStudentAcceptanceCreateMembershipsForEachRolePath() {
        assertMembershipAcceptance(
            InvitationTargetRole.PROFESSOR,
            GroupClassMemberRole.PROFESSOR,
            WorkspaceDestination.PROFESSOR);
        assertMembershipAcceptance(
            InvitationTargetRole.STUDENT,
            GroupClassMemberRole.STUDENT,
            WorkspaceDestination.STUDENT);
    }

    @Test
    void br03_br04_br05_br06_roleRoutingPriorityIsDeterministic() {
        var account = account("priority@test.local");
        account.setSystemAdmin(false);

        var tenantAdminRole = new TenantAccountRole();
        tenantAdminRole.setRole(role("TENANT_ADMIN"));
        tenantAdminRole.setTenantAccount(tenantAccount(account, tenant("Tenant A")));
        tenantAdminRole.getTenantAccount().setJoinedAt(Instant.now());
        when(tenantAccountRoleRepository.findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(account.getId()))
                .thenReturn(List.of(tenantAdminRole));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var routingService = new WorkspaceRoutingService(authenticatedAccountService,
                accountRepository,
                tenantAccountRepository,
                tenantAccountRoleRepository,
                groupClassMemberRepository);
        assertEquals(WorkspaceDestination.TENANT_ADMIN, routingService.resolveForAccount(account).destination());

        account.setSystemAdmin(true);
        assertEquals(WorkspaceDestination.SYSTEM_ADMIN, routingService.resolveForAccount(account).destination());
    }

    @Test
    void br03_br04_br05_br06_multiRoleUsersCanPrepareAlternateWorkspaceContext() {
        var account = account("multi-role@test.local");
        var tenantAdminRole = new TenantAccountRole();
        tenantAdminRole.setRole(role("TENANT_ADMIN"));
        tenantAdminRole.setTenantAccount(tenantAccount(account, tenant("Tenant A")));
        tenantAdminRole.setAssignedAt(Instant.now());
        var professorMembership =
                membership(account, tenantAdminRole.getTenantAccount().getTenant(), GroupClassMemberRole.PROFESSOR);

        when(tenantAccountRoleRepository.findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(account.getId()))
                .thenReturn(List.of(tenantAdminRole));
        when(groupClassMemberRepository.findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(account.getId()))
                .thenReturn(List.of(professorMembership));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var routingService = new WorkspaceRoutingService(authenticatedAccountService,
                accountRepository,
                tenantAccountRepository,
                tenantAccountRoleRepository,
                groupClassMemberRepository);

        assertEquals(WorkspaceDestination.TENANT_ADMIN, routingService.resolveForAccount(account).destination());
        assertTrue(routingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR));
        assertEquals(professorMembership.getId(), account.getLastGroupClassMember().getId());
    }

    @Test
    void br19_br20_br21_br22_professorClassVisibilityAndLogicalRemovalAreEnforced() {
        var professorAccount = account("professor@test.local");
        var tenant = tenant("Algorithms University");
        var professorMembership = membership(professorAccount, tenant, GroupClassMemberRole.PROFESSOR);
        var studentMembership = membership(account("student@test.local"), tenant, GroupClassMemberRole.STUDENT);
        studentMembership.setGroupClass(professorMembership.getGroupClass());
        when(workspaceRoutingService.currentClassMembership(professorAccount, GroupClassMemberRole.PROFESSOR))
                .thenReturn(Optional.of(professorMembership));
        when(
            groupClassMemberRepository
                    .findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(professorMembership.getGroupClass().getId()))
                .thenReturn(List.of(studentMembership));
        when(groupClassMemberRepository.findById(studentMembership.getId())).thenReturn(Optional.of(studentMembership));
        when(groupClassMemberRepository.save(any(GroupClassMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var service = new ProfessorWorkspaceService(workspaceRoutingService,
                tenantAccountRepository,
                groupClassMemberRepository,
                invitationService);

        assertEquals(1, service.listStudents(professorAccount).size());
        service.disableStudentMembership(professorAccount, studentMembership.getId());
        assertTrue(studentMembership.isLocked());
        assertEquals(professorMembership.getGroupClass().getId(), studentMembership.getGroupClass().getId());
    }

    @Test
    void br23_br54_studentWithoutMembershipGetsNoAccessRoute() {
        var account = account("student@test.local");
        when(tenantAccountRoleRepository.findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(account.getId()))
                .thenReturn(List.of());
        when(groupClassMemberRepository.findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(account.getId()))
                .thenReturn(List.of());

        var routingService = new WorkspaceRoutingService(authenticatedAccountService,
                accountRepository,
                tenantAccountRepository,
                tenantAccountRoleRepository,
                groupClassMemberRepository);
        assertEquals(WorkspaceDestination.NO_ACCESS, routingService.resolveForAccount(account).destination());
    }

    private void assertMembershipAcceptance(
            InvitationTargetRole targetRole,
            GroupClassMemberRole expectedRole,
            WorkspaceDestination destination) {
        var account = account(targetRole.name().toLowerCase() + "@test.local");
        var invitation = pendingInvitation(targetRole, InvitationStatus.PENDING);
        onboardingSessionContext.setInvitationId(invitation.getId());
        onboardingSessionContext.setInvitedEmail(invitation.getInvitedEmail());
        when(authenticatedAccountService.requireCurrentAccount()).thenReturn(account);
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(tenantAccountRepository.findByTenant_IdAndAccount_Id(invitation.getTenant().getId(), account.getId()))
                .thenReturn(Optional.empty());
        when(tenantAccountRepository.save(any(TenantAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByCode(targetRole.name())).thenReturn(Optional.of(role(targetRole.name())));
        when(tenantAccountRoleRepository.findByTenantAccount_IdAndRole_Code(any(), eq(targetRole.name())))
                .thenReturn(Optional.empty());
        when(tenantAccountRoleRepository.save(any(TenantAccountRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(groupClassMemberRepository.findByGroupClass_IdAndTenantAccount_Id(any(), any()))
                .thenReturn(Optional.empty());
        when(groupClassMemberRepository.save(any(GroupClassMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workspaceRoutingService.resolveForAccount(account))
                .thenReturn(new WorkspaceDecision(destination, UUID.randomUUID(), UUID.randomUUID()));

        var decision = invitationService.completePendingInvitationForCurrentAccount();

        assertEquals(destination, decision.destination());
        assertNotNull(account.getLastGroupClassMember());
        assertEquals(expectedRole, account.getLastGroupClassMember().getRole());
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
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

    private static Invitation pendingInvitation(InvitationTargetRole targetRole, InvitationStatus status) {
        var invitation = new Invitation();
        invitation.setId(1L);
        invitation.setTenant(tenant("Algorithms University"));
        invitation.setGroupClass(groupClass(invitation.getTenant()));
        invitation.setInvitedEmail(targetRole.name().toLowerCase() + "@test.local");
        invitation.setTargetRole(targetRole);
        invitation.setTokenHash("token-hash");
        invitation.setStatus(status);
        invitation.setExpiresAt(Instant.now().plusSeconds(3600));
        invitation.setCreatedAt(Instant.now());
        invitation.setUpdatedAt(Instant.now());
        return invitation;
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

    private static GroupClass groupClass(Tenant tenant) {
        var groupClass = new GroupClass();
        groupClass.setId(UUID.randomUUID());
        groupClass.setTenant(tenant);
        groupClass.setCode("ALG-101-A");
        groupClass.setName("Algorithms 101");
        groupClass.setActive(true);
        groupClass.setCreatedAt(Instant.now());
        groupClass.setUpdatedAt(Instant.now());
        return groupClass;
    }

    private static GroupClassMember membership(Account account, Tenant tenant, GroupClassMemberRole role) {
        var membership = new GroupClassMember();
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
