package com.wornux.services.onboarding;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.config.SocraticEmailProperties;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.authorization.GroupClassMemberRole;
import com.wornux.data.entities.authorization.GroupClassMemberRoleId;
import com.wornux.data.entities.authorization.TenantAccountRole;
import com.wornux.data.entities.authorization.TenantAccountRoleId;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.onboarding.Invitation;
import com.wornux.data.entities.onboarding.InvitationStatus;
import com.wornux.data.entities.onboarding.InvitationTargetRole;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.academic.GroupClassRepository;
import com.wornux.data.repositories.authorization.GroupClassMemberRoleRepository;
import com.wornux.data.repositories.authorization.RoleRepository;
import com.wornux.data.repositories.authorization.TenantAccountRoleRepository;
import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import com.wornux.data.repositories.identity.TenantRepository;
import com.wornux.data.repositories.onboarding.InvitationRepository;
import com.wornux.services.email.EmailSendException;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.security.RoleNamespaceService;
import com.wornux.services.workspace.WorkspaceDecision;
import com.wornux.services.workspace.WorkspaceRoutingService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

    private final SocraticEmailProperties emailProperties;
    private final InvitationRepository invitationRepository;
    private final AccountRepository accountRepository;
    private final TenantRepository tenantRepository;
    private final TenantAccountRepository tenantAccountRepository;
    private final GroupClassRepository groupClassRepository;
    private final RoleRepository roleRepository;
    private final TenantAccountRoleRepository tenantAccountRoleRepository;
    private final GroupClassMemberRoleRepository groupClassMemberRoleRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;
    private final InvitationTokenService invitationTokenService;
    private final InvitationEmailService invitationEmailService;
    private final OnboardingSessionContext onboardingSessionContext;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticatedAccountService authenticatedAccountService;
    private final WorkspaceRoutingService workspaceRoutingService;
    private final RoleNamespaceService roleNamespaceService;

    public InvitationService(
            SocraticEmailProperties emailProperties,
            InvitationRepository invitationRepository,
            AccountRepository accountRepository,
            TenantRepository tenantRepository,
            TenantAccountRepository tenantAccountRepository,
            GroupClassRepository groupClassRepository,
            RoleRepository roleRepository,
            TenantAccountRoleRepository tenantAccountRoleRepository,
            GroupClassMemberRoleRepository groupClassMemberRoleRepository,
            GroupClassMemberRepository groupClassMemberRepository,
            InvitationTokenService invitationTokenService,
            InvitationEmailService invitationEmailService,
            OnboardingSessionContext onboardingSessionContext,
            PasswordEncoder passwordEncoder,
            AuthenticatedAccountService authenticatedAccountService,
            WorkspaceRoutingService workspaceRoutingService,
            RoleNamespaceService roleNamespaceService) {
        this.emailProperties = emailProperties;
        this.invitationRepository = invitationRepository;
        this.accountRepository = accountRepository;
        this.tenantRepository = tenantRepository;
        this.tenantAccountRepository = tenantAccountRepository;
        this.groupClassRepository = groupClassRepository;
        this.roleRepository = roleRepository;
        this.tenantAccountRoleRepository = tenantAccountRoleRepository;
        this.groupClassMemberRoleRepository = groupClassMemberRoleRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
        this.invitationTokenService = invitationTokenService;
        this.invitationEmailService = invitationEmailService;
        this.onboardingSessionContext = onboardingSessionContext;
        this.passwordEncoder = passwordEncoder;
        this.authenticatedAccountService = authenticatedAccountService;
        this.workspaceRoutingService = workspaceRoutingService;
        this.roleNamespaceService = roleNamespaceService;
    }

    @Transactional
    public Invitation createInvitation(
            InvitationTargetRole targetRole,
            UUID tenantId,
            UUID groupClassId,
            String invitedEmail,
            Account invitedByAccount,
            TenantAccount invitedByTenantAccount,
            GroupClassMember invitedByGroupClassMember) {
        var rawToken = invitationTokenService.generateRawToken();
        var invitation = new Invitation();
        invitation.setTenant(
            tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("The target tenant was not found.")));
        if (groupClassId != null) {
            invitation.setGroupClass(
                groupClassRepository.findById(groupClassId)
                        .orElseThrow(() -> new IllegalArgumentException("The target class was not found.")));
        }
        invitation.setInvitedEmail(invitedEmail.trim().toLowerCase(java.util.Locale.ROOT));
        invitation.setTargetRole(targetRole);
        invitation.setTokenHash(invitationTokenService.hash(rawToken));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(emailProperties.getInvitationExpiration()));
        invitation.setInvitedByAccount(invitedByAccount);
        invitation.setInvitedByTenantAccount(invitedByTenantAccount);
        invitation.setInvitedByGroupClassMember(invitedByGroupClassMember);
        invitation.setCreatedAt(Instant.now());
        invitation.setUpdatedAt(Instant.now());
        invitation = invitationRepository.save(invitation);
        try {
            invitationEmailService.sendInvitation(invitation, rawToken);
            return invitation;
        }
        catch (EmailSendException exception) {
            invitation.setStatus(InvitationStatus.DELIVERY_FAILED);
            invitation.setDeliveryError(exception.getMessage());
            invitation.setUpdatedAt(Instant.now());
            invitationRepository.save(invitation);
            throw exception;
        }
    }

    @Transactional
    public OnboardingStart prepareOnboarding(String rawToken) {
        var invitation = validateInvitation(rawToken);
        onboardingSessionContext.setInvitationId(invitation.getId());
        onboardingSessionContext.setInvitedEmail(invitation.getInvitedEmail());
        onboardingSessionContext.setTargetRole(invitation.getTargetRole());
        onboardingSessionContext.setTenantId(invitation.getTenant().getId());
        onboardingSessionContext
                .setGroupClassId(invitation.getGroupClass() == null ? null : invitation.getGroupClass().getId());
        onboardingSessionContext.setPostAcceptRedirect(defaultRedirect(invitation.getTargetRole()));
        onboardingSessionContext.setValidatedAt(Instant.now());
        onboardingSessionContext
                .setAccountAlreadyExists(accountRepository.findByEmail(invitation.getInvitedEmail()).isPresent());
        return new OnboardingStart(invitation.getId(),
                invitation.getInvitedEmail(),
                invitation.getTargetRole(),
                onboardingSessionContext.isAccountAlreadyExists());
    }

    @Transactional
    public Account registerInvitedAccount(String firstName, String lastName, String password, String confirmPassword) {
        if (!onboardingSessionContext.hasActiveInvitation()) {
            throw new InvitationStateException("A validated invitation is required before registration.");
        }
        var invitation = invitationRepository.findById(onboardingSessionContext.getInvitationId())
                .orElseThrow(() -> new InvitationStateException("The invitation could not be found."));
        validateInvitationState(invitation);
        if (!invitation.getInvitedEmail().equalsIgnoreCase(onboardingSessionContext.getInvitedEmail())) {
            throw new InvitationStateException("The invitation email no longer matches the active onboarding state.");
        }
        if (onboardingSessionContext.isAccountAlreadyExists()) {
            throw new InvitationStateException(
                    "This invitation already belongs to an existing account. Please sign in.");
        }
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new InvitationStateException("First name and last name are required.");
        }
        if (password == null || password.isBlank() || !password.equals(confirmPassword)) {
            throw new InvitationStateException("Password and confirmation must match.");
        }
        if (accountRepository.findByEmail(invitation.getInvitedEmail()).isPresent()) {
            throw new InvitationStateException(
                    "This invited email already belongs to an existing account. Please sign in.");
        }

        var account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail(invitation.getInvitedEmail());
        account.setFirstName(firstName.trim());
        account.setLastName(lastName.trim());
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setLocked(false);
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        onboardingSessionContext.setAccountAlreadyExists(true);
        return accountRepository.save(account);
    }

    @Transactional
    public WorkspaceDecision completePendingInvitationForCurrentAccount() {
        var account = authenticatedAccountService.requireCurrentAccount();
        if (!onboardingSessionContext.hasActiveInvitation()) {
            return workspaceRoutingService.resolveForAccount(account);
        }
        if (!account.getEmail().equalsIgnoreCase(onboardingSessionContext.getInvitedEmail())) {
            throw new InvitationStateException(
                    "Please sign in with the invited email address to accept this invitation.");
        }

        var invitation = invitationRepository.findById(onboardingSessionContext.getInvitationId())
                .orElseThrow(() -> new InvitationStateException("The invitation could not be found."));
        validateInvitationState(invitation);

        var tenantAccount =
                tenantAccountRepository.findByTenant_IdAndAccount_Id(invitation.getTenant().getId(), account.getId())
                        .orElseGet(() -> createTenantAccount(account, invitation.getTenant().getId()));
        assignTenantRoleIfNeeded(tenantAccount, invitation.getTargetRole(), invitation.getInvitedByTenantAccount());

        GroupClassMember groupClassMember = null;
        if (invitation.getTargetRole() == InvitationTargetRole.PROFESSOR
                || invitation.getTargetRole() == InvitationTargetRole.STUDENT) {
            groupClassMember = createOrReuseMembership(tenantAccount, invitation);
            assignGroupClassRoleIfNeeded(
                groupClassMember,
                invitation.getTargetRole(),
                invitation.getInvitedByGroupClassMember());
        }
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitation.setUpdatedAt(Instant.now());
        invitationRepository.save(invitation);
        onboardingSessionContext.clear();
        return workspaceRoutingService.resolveForAccount(account);
    }

    @Transactional(readOnly = true)
    public List<Invitation> listTenantInvitations(UUID tenantId) {
        return invitationRepository.findByTenant_IdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Invitation> listClassInvitations(UUID groupClassId) {
        return invitationRepository.findByGroupClass_IdOrderByCreatedAtDesc(groupClassId);
    }

    private Invitation validateInvitation(String rawToken) {
        var invitation = invitationRepository.findByTokenHash(invitationTokenService.hash(rawToken))
                .orElseThrow(() -> new InvitationStateException("This invitation link is invalid."));
        validateInvitationState(invitation);
        return invitation;
    }

    private void validateInvitationState(Invitation invitation) {
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new InvitationStateException("This invitation has already been accepted.");
        }
        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new InvitationStateException("This invitation has been revoked.");
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitation.setUpdatedAt(Instant.now());
            invitationRepository.save(invitation);
            throw new InvitationStateException("This invitation has expired.");
        }
    }

    private TenantAccount createTenantAccount(Account account, UUID tenantId) {
        var tenantAccount = new TenantAccount();
        tenantAccount.setId(UUID.randomUUID());
        tenantAccount.setAccount(account);
        tenantAccount.setTenant(
            tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("The target tenant was not found.")));
        tenantAccount.setLocked(false);
        tenantAccount.setJoinedAt(Instant.now());
        tenantAccount.setUpdatedAt(Instant.now());
        return tenantAccountRepository.save(tenantAccount);
    }

    private void assignTenantRoleIfNeeded(
            TenantAccount tenantAccount,
            InvitationTargetRole targetRole,
            TenantAccount assignedBy) {
        var roleCode = targetRole.name();
        var role = roleRepository
                .findByRoleNamespace_IdAndCode(tenantAccount.getTenant().getRoleNamespace().getId(), roleCode)
                .orElseThrow(() -> new IllegalStateException("Missing role %s".formatted(roleCode)));
        if (tenantAccountRoleRepository.findByTenantAccount_IdAndRole_Code(tenantAccount.getId(), roleCode)
                .isPresent()) {
            return;
        }
        var tenantAccountRole = new TenantAccountRole();
        var id = new TenantAccountRoleId();
        id.setTenantAccountId(tenantAccount.getId());
        id.setRoleId(role.getId());
        tenantAccountRole.setId(id);
        tenantAccountRole.setTenantAccount(tenantAccount);
        tenantAccountRole.setRole(role);
        tenantAccountRole.setAssignedByTenantAccount(assignedBy);
        tenantAccountRole.setAssignedAt(Instant.now());
        tenantAccountRoleRepository.save(tenantAccountRole);
        roleNamespaceService.recordRbacChange(role.getRoleNamespace().getId());
    }

    private void assignGroupClassRoleIfNeeded(
            GroupClassMember groupClassMember,
            InvitationTargetRole targetRole,
            GroupClassMember assignedBy) {
        var roleCode = targetRole.name();
        var role =
                roleRepository
                        .findByRoleNamespace_IdAndCode(
                            groupClassMember.getTenantAccount().getTenant().getRoleNamespace().getId(),
                            roleCode)
                        .orElseThrow(() -> new IllegalStateException("Missing role %s".formatted(roleCode)));
        if (groupClassMemberRoleRepository.findByGroupClassMember_IdAndRole_Code(groupClassMember.getId(), roleCode)
                .isPresent()) {
            return;
        }
        var groupClassMemberRole = new GroupClassMemberRole();
        var id = new GroupClassMemberRoleId();
        id.setGroupClassMemberId(groupClassMember.getId());
        id.setRoleId(role.getId());
        groupClassMemberRole.setId(id);
        groupClassMemberRole.setGroupClassMember(groupClassMember);
        groupClassMemberRole.setRole(role);
        groupClassMemberRole.setAssignedByGroupClassMember(assignedBy);
        groupClassMemberRole.setAssignedAt(Instant.now());
        groupClassMemberRoleRepository.save(groupClassMemberRole);
        roleNamespaceService.recordRbacChange(role.getRoleNamespace().getId());
    }

    private GroupClassMember createOrReuseMembership(TenantAccount tenantAccount, Invitation invitation) {
        if (invitation.getGroupClass() == null) {
            throw new InvitationStateException("A class invitation must include a target class.");
        }
        var memberRole = invitation.getTargetRole() == InvitationTargetRole.PROFESSOR
                ? GroupClassMemberKind.PROFESSOR
                : GroupClassMemberKind.STUDENT;
        return groupClassMemberRepository
                .findByGroupClass_IdAndTenantAccount_Id(invitation.getGroupClass().getId(), tenantAccount.getId())
                .filter(existing -> existing.getMemberKind() == memberRole)
                .map(existing -> {
                    existing.setLocked(false);
                    existing.setUpdatedAt(Instant.now());
                    return groupClassMemberRepository.save(existing);
                })
                .orElseGet(() -> createMembership(tenantAccount, invitation.getGroupClass().getId(), memberRole));
    }

    private GroupClassMember createMembership(
            TenantAccount tenantAccount,
            UUID groupClassId,
            GroupClassMemberKind memberRole) {
        var membership = new GroupClassMember();
        membership.setId(UUID.randomUUID());
        membership.setGroupClass(new GroupClass());
        membership.getGroupClass().setId(groupClassId);
        membership.setTenantAccount(tenantAccount);
        membership.setMemberKind(memberRole);
        membership.setLocked(false);
        membership.setJoinedAt(Instant.now());
        membership.setUpdatedAt(Instant.now());
        return groupClassMemberRepository.save(membership);
    }

    private String defaultRedirect(InvitationTargetRole targetRole) {
        return switch (targetRole) {
            case TENANT_ADMIN -> "tenant";
            case PROFESSOR -> "professor";
            case STUDENT -> "student";
        };
    }
}
