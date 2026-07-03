package com.wornux.security.authorization;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.authorization.GroupClassMemberRole;
import com.wornux.data.entities.authorization.GroupClassMemberRoleId;
import com.wornux.data.entities.authorization.PlatformSettings;
import com.wornux.data.entities.authorization.Role;
import com.wornux.data.entities.authorization.RoleAssignmentLevel;
import com.wornux.data.entities.authorization.RoleNamespace;
import com.wornux.data.entities.authorization.TenantAccountRole;
import com.wornux.data.entities.authorization.TenantAccountRoleId;
import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.academic.GroupClassRepository;
import com.wornux.data.repositories.authorization.GroupClassMemberRoleRepository;
import com.wornux.data.repositories.authorization.PlatformSettingsRepository;
import com.wornux.data.repositories.authorization.RoleNamespaceRepository;
import com.wornux.data.repositories.authorization.RoleRepository;
import com.wornux.data.repositories.authorization.TenantAccountRoleRepository;
import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import com.wornux.data.repositories.identity.TenantRepository;
import com.wornux.security.permission.AppPermission;
import com.wornux.security.permission.AppResource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleAdministrationService {

    private final AuthorizationService authorizationService;
    private final ActiveContextHolder activeContextHolder;
    private final RoleRepository roleRepository;
    private final RoleNamespaceRepository roleNamespaceRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final TenantAccountRepository tenantAccountRepository;
    private final GroupClassRepository groupClassRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;
    private final TenantAccountRoleRepository tenantAccountRoleRepository;
    private final GroupClassMemberRoleRepository groupClassMemberRoleRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RoleAdministrationService(
            AuthorizationService authorizationService,
            ActiveContextHolder activeContextHolder,
            RoleRepository roleRepository,
            RoleNamespaceRepository roleNamespaceRepository,
            PlatformSettingsRepository platformSettingsRepository,
            TenantRepository tenantRepository,
            AccountRepository accountRepository,
            TenantAccountRepository tenantAccountRepository,
            GroupClassRepository groupClassRepository,
            GroupClassMemberRepository groupClassMemberRepository,
            TenantAccountRoleRepository tenantAccountRoleRepository,
            GroupClassMemberRoleRepository groupClassMemberRoleRepository,
            ApplicationEventPublisher eventPublisher) {
        this.authorizationService = authorizationService;
        this.activeContextHolder = activeContextHolder;
        this.roleRepository = roleRepository;
        this.roleNamespaceRepository = roleNamespaceRepository;
        this.platformSettingsRepository = platformSettingsRepository;
        this.tenantRepository = tenantRepository;
        this.accountRepository = accountRepository;
        this.tenantAccountRepository = tenantAccountRepository;
        this.groupClassRepository = groupClassRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
        this.tenantAccountRoleRepository = tenantAccountRoleRepository;
        this.groupClassMemberRoleRepository = groupClassMemberRoleRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(AppPermission.ROLE_VIEW)
    public List<Role> rolesForActiveContext(RoleAssignmentLevel visibleLevel) {
        var namespace = activeNamespace();
        if (activeContext().level() == ContextLevel.PLATFORM) {
            return roleRepository.findByRoleNamespace_IdAndAssignmentLevelAndActiveTrue(namespace.getId(), RoleAssignmentLevel.PLATFORM);
        }
        return roleRepository.findByRoleNamespace_IdAndActiveTrue(namespace.getId()).stream()
                .filter(role -> role.getAssignmentLevel() == visibleLevel)
                .toList();
    }

    @Transactional
    @RequiresPermission(AppPermission.ROLE_CREATE)
    public Role createRole(CreateRoleCommand command) {
        var actor = authorizationService.snapshot();
        var namespace = activeNamespace();
        validateCreateContext(command.assignmentLevel());
        validatePermissionCodes(command.permissions(), command.assignmentLevel(), actor);
        enforcePriorityBoundary(actor, namespace.getId(), command.priority());
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        var now = Instant.now();
        var role = new Role();
        role.setId(UUID.randomUUID());
        role.setRoleNamespace(namespace);
        role.setCode(uniqueCode(namespace.getId(), command.name()));
        role.setName(command.name().trim());
        role.setDescription(blankToNull(command.description()));
        role.setAssignmentLevel(command.assignmentLevel());
        role.setPriority(command.priority());
        role.setPermissions(command.permissions().stream().sorted().toArray(String[]::new));
        role.setSystemDefined(false);
        role.setAssignable(true);
        role.setActive(true);
        role.setCreatedByAccount(accountRepository.findById(actor.accountId()).orElse(null));
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        var saved = roleRepository.save(role);
        changed(namespace.getId());
        return saved;
    }

    @Transactional
    @RequiresPermission(AppPermission.ROLE_UPDATE)
    public Role updateRole(UpdateRoleCommand command) {
        var actor = authorizationService.snapshot();
        var role = roleRepository.findById(command.roleId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown role %s".formatted(command.roleId())));
        validatePermissionCodes(command.permissions(), role.getAssignmentLevel(), actor);
        enforcePriorityBoundary(actor, role.getRoleNamespace().getId(), role.getPriority());
        if (role.isSystemDefined() && actor.activeContext().level() != ContextLevel.PLATFORM) {
            throw new AccessDeniedException("System-defined roles are locked in this context");
        }
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        role.setName(command.name().trim());
        role.setDescription(blankToNull(command.description()));
        role.setActive(command.active());
        role.setAssignable(command.assignable());
        role.setPriority(command.priority());
        role.setPermissions(command.permissions().stream().sorted().toArray(String[]::new));
        role.setUpdatedAt(Instant.now());
        var saved = roleRepository.save(role);
        changed(role.getRoleNamespace().getId());
        return saved;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(AppPermission.ROLE_ASSIGN)
    public TenantRoleAssignmentMatrix tenantAssignments() {
        var actor = authorizationService.snapshot();
        if (actor.activeContext().level() == ContextLevel.PLATFORM || actor.tenantId() == null) {
            throw new AccessDeniedException("Tenant role assignments require a tenant context");
        }
        var namespace = activeNamespace();
        var roles = roleRepository.findByRoleNamespace_IdAndAssignmentLevelAndActiveTrue(namespace.getId(), RoleAssignmentLevel.TENANT).stream()
                .filter(Role::isAssignable)
                .toList();
        var members = tenantAccountRepository.findByTenant_IdAndLockedFalseOrderByJoinedAtAsc(actor.tenantId());
        return new TenantRoleAssignmentMatrix(members, roles);
    }

    @Transactional
    @RequiresPermission(AppPermission.ROLE_ASSIGN)
    public void setTenantRole(UUID tenantAccountId, UUID roleId, boolean assigned) {
        var actor = authorizationService.snapshot();
        var tenantAccount = tenantAccountRepository.findById(tenantAccountId).orElseThrow();
        if (!tenantAccount.getTenant().getId().equals(actor.tenantId())) {
            throw new AccessDeniedException("Cannot assign roles outside the active tenant");
        }
        var role = assignableRole(roleId, RoleAssignmentLevel.TENANT);
        enforcePriorityBoundary(actor, role.getRoleNamespace().getId(), role.getPriority());
        var id = new TenantAccountRoleId();
        id.setTenantAccountId(tenantAccountId);
        id.setRoleId(roleId);
        if (assigned && !tenantAccountRoleRepository.existsById(id)) {
            var assignment = new TenantAccountRole();
            assignment.setId(id);
            assignment.setTenantAccount(tenantAccount);
            assignment.setRole(role);
            if (actor.tenantAccountId() != null) {
                assignment.setAssignedByTenantAccount(tenantAccountRepository.findById(actor.tenantAccountId()).orElse(null));
            }
            assignment.setAssignedAt(Instant.now());
            tenantAccountRoleRepository.save(assignment);
            changed(role.getRoleNamespace().getId());
        }
        if (!assigned && tenantAccountRoleRepository.existsById(id)) {
            tenantAccountRoleRepository.deleteById(id);
            changed(role.getRoleNamespace().getId());
        }
    }

    @Transactional(readOnly = true)
    @RequiresPermission(AppPermission.ROLE_ASSIGN)
    public GroupClassRoleAssignmentMatrix groupClassAssignments(UUID groupClassId) {
        var actor = authorizationService.snapshot();
        ensureGroupClassAccessible(actor, groupClassId);
        var namespace = activeNamespace();
        var roles = roleRepository.findByRoleNamespace_IdAndAssignmentLevelAndActiveTrue(namespace.getId(), RoleAssignmentLevel.GROUP_CLASS).stream()
                .filter(Role::isAssignable)
                .toList();
        var members = groupClassMemberRepository.findByGroupClass_IdAndLockedFalseOrderByJoinedAtAsc(groupClassId);
        return new GroupClassRoleAssignmentMatrix(members, roles);
    }

    @Transactional
    @RequiresPermission(AppPermission.ROLE_ASSIGN)
    public void setGroupClassRole(UUID groupClassMemberId, UUID roleId, boolean assigned) {
        var actor = authorizationService.snapshot();
        var member = groupClassMemberRepository.findById(groupClassMemberId).orElseThrow();
        ensureGroupClassAccessible(actor, member.getGroupClass().getId());
        var role = assignableRole(roleId, RoleAssignmentLevel.GROUP_CLASS);
        enforcePriorityBoundary(actor, role.getRoleNamespace().getId(), role.getPriority());
        var id = new GroupClassMemberRoleId();
        id.setGroupClassMemberId(groupClassMemberId);
        id.setRoleId(roleId);
        if (assigned && !groupClassMemberRoleRepository.existsById(id)) {
            var assignment = new GroupClassMemberRole();
            assignment.setId(id);
            assignment.setGroupClassMember(member);
            assignment.setRole(role);
            if (actor.groupClassMemberId() != null) {
                assignment.setAssignedByGroupClassMember(groupClassMemberRepository.findById(actor.groupClassMemberId()).orElse(null));
            }
            assignment.setAssignedAt(Instant.now());
            groupClassMemberRoleRepository.save(assignment);
            changed(role.getRoleNamespace().getId());
        }
        if (!assigned && groupClassMemberRoleRepository.existsById(id)) {
            groupClassMemberRoleRepository.deleteById(id);
            changed(role.getRoleNamespace().getId());
        }
    }

    @Transactional(readOnly = true)
    public Set<UUID> tenantAccountRoleIds(UUID tenantAccountId) {
        return tenantAccountRoleRepository.findByTenantAccount_IdAndRole_ActiveTrue(tenantAccountId).stream()
                .map(assignment -> assignment.getRole().getId())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public Set<UUID> groupClassMemberRoleIds(UUID groupClassMemberId) {
        return groupClassMemberRoleRepository.findByGroupClassMember_Id(groupClassMemberId).stream()
                .map(assignment -> assignment.getRole().getId())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public List<GroupClass> activeTenantClasses() {
        var actor = authorizationService.snapshot();
        if (actor.tenantId() == null) {
            return List.of();
        }
        return groupClassRepository.findByTenant_IdOrderByNameAsc(actor.tenantId());
    }

    public boolean permissionValidForLevel(String code, RoleAssignmentLevel level) {
        var permission = permissionByCode(code);
        if (permission == null) {
            return false;
        }
        if (level == RoleAssignmentLevel.PLATFORM) {
            return Set.of(AppResource.TENANT, AppResource.ACCOUNT, AppResource.ROLE).contains(permission.resource());
        }
        if (level == RoleAssignmentLevel.GROUP_CLASS) {
            return Set.of(
                    AppResource.GROUP_CLASS,
                    AppResource.GROUP_CLASS_MEMBER,
                    AppResource.GROUP_CLASS_JOIN_CODE,
                    AppResource.GROUNDING,
                    AppResource.CONVERSATION,
                    AppResource.TRAINING_ACTIVITY,
                    AppResource.TRAINING_ACTIVITY_ASSIGNMENT,
                    AppResource.COURSE_MATERIAL).contains(permission.resource());
        }
        return permission.resource() != AppResource.TENANT || permission != AppPermission.TENANT_CREATE;
    }

    public String disabledReason(Role role, AppPermission permission) {
        var actor = authorizationService.snapshot();
        if (!actor.permissionCodes().contains(permission.code())) {
            return "actor lacks permission";
        }
        if (!permissionValidForLevel(permission.code(), role.getAssignmentLevel())) {
            return "invalid for role assignment level";
        }
        if (!canManagePriority(actor, role.getRoleNamespace().getId(), role.getPriority())) {
            return "target role priority is too high";
        }
        if (role.isSystemDefined() && actor.activeContext().level() != ContextLevel.PLATFORM) {
            return "system-defined role is locked";
        }
        return null;
    }

    private ActiveContext activeContext() {
        return activeContextHolder.current().orElseThrow(() -> new AccessDeniedException("Active context is required"));
    }

    private RoleNamespace activeNamespace() {
        var context = activeContext();
        if (context.level() == ContextLevel.PLATFORM) {
            return platformSettingsRepository.findById(Boolean.TRUE).map(PlatformSettings::getRoleNamespace).orElseThrow();
        }
        return tenantRepository.findById(context.tenantId()).orElseThrow().getRoleNamespace();
    }

    private void validateCreateContext(RoleAssignmentLevel assignmentLevel) {
        var context = activeContext();
        if (context.level() == ContextLevel.PLATFORM && assignmentLevel != RoleAssignmentLevel.PLATFORM) {
            throw new AccessDeniedException("Platform context can create platform roles only");
        }
        if (context.level() == ContextLevel.TENANT && assignmentLevel == RoleAssignmentLevel.PLATFORM) {
            throw new AccessDeniedException("Tenant context cannot create platform roles");
        }
        if (context.level() == ContextLevel.GROUP_CLASS) {
            throw new AccessDeniedException("Create roles from tenant role management");
        }
    }

    private void validatePermissionCodes(Set<String> permissionCodes, RoleAssignmentLevel level, UserAccessSnapshot actor) {
        var knownCodes = Arrays.stream(AppPermission.values()).map(AppPermission::code).collect(Collectors.toSet());
        if (!knownCodes.containsAll(permissionCodes)) {
            throw new IllegalArgumentException("Permissions must be known AppPermission codes");
        }
        var invalid = permissionCodes.stream().filter(code -> !permissionValidForLevel(code, level)).findFirst();
        if (invalid.isPresent()) {
            throw new IllegalArgumentException("Permission %s is invalid for %s roles".formatted(invalid.get(), level));
        }
        if (!actor.permissionCodes().containsAll(permissionCodes)) {
            throw new AccessDeniedException("Cannot grant permissions outside the actor snapshot");
        }
    }

    private Role assignableRole(UUID roleId, RoleAssignmentLevel assignmentLevel) {
        var role = roleRepository.findById(roleId).orElseThrow();
        if (role.getAssignmentLevel() != assignmentLevel || !role.isActive() || !role.isAssignable()) {
            throw new AccessDeniedException("Role is not assignable at %s level".formatted(assignmentLevel));
        }
        return role;
    }

    private void ensureGroupClassAccessible(UserAccessSnapshot actor, UUID groupClassId) {
        var groupClass = groupClassRepository.findById(groupClassId).orElseThrow();
        if (!groupClass.getTenant().getId().equals(actor.tenantId())) {
            throw new AccessDeniedException("Group class is outside the active tenant");
        }
        if (actor.activeContext().level() == ContextLevel.GROUP_CLASS && !groupClassId.equals(actor.groupClassId())) {
            throw new AccessDeniedException("Group-class context can manage only the active class");
        }
    }

    private void enforcePriorityBoundary(UserAccessSnapshot actor, UUID roleNamespaceId, int targetPriority) {
        if (!canManagePriority(actor, roleNamespaceId, targetPriority)) {
            throw new AccessDeniedException("Cannot manage a role at or above the actor priority");
        }
    }

    private boolean canManagePriority(UserAccessSnapshot actor, UUID roleNamespaceId, int targetPriority) {
        var actorHighestPriority = roleRepository.findByRoleNamespace_IdAndActiveTrue(roleNamespaceId).stream()
                .filter(role -> actor.roleCodes().contains(role.getCode()))
                .mapToInt(Role::getPriority)
                .max()
                .orElse(-1);
        return targetPriority < actorHighestPriority;
    }

    private String uniqueCode(UUID namespaceId, String name) {
        var base = slug(name);
        var code = base;
        var suffix = 2;
        while (roleRepository.findByRoleNamespace_IdAndCode(namespaceId, code).isPresent()) {
            code = base + "-" + suffix++;
        }
        return code;
    }

    private String slug(String value) {
        var normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        var slug = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "role" : slug;
    }

    private AppPermission permissionByCode(String code) {
        return Arrays.stream(AppPermission.values()).filter(permission -> permission.code().equals(code)).findFirst().orElse(null);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void changed(UUID roleNamespaceId) {
        roleNamespaceRepository.incrementRbacVersion(roleNamespaceId);
        eventPublisher.publishEvent(new RbacChangedEvent(roleNamespaceId));
    }

    public record CreateRoleCommand(String name, String description, RoleAssignmentLevel assignmentLevel, int priority, Set<String> permissions) {
        public CreateRoleCommand {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record UpdateRoleCommand(UUID roleId, String name, String description, boolean active, boolean assignable, int priority, Set<String> permissions) {
        public UpdateRoleCommand {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record TenantRoleAssignmentMatrix(List<TenantAccount> members, List<Role> roles) {
    }

    public record GroupClassRoleAssignmentMatrix(List<GroupClassMember> members, List<Role> roles) {
    }
}
