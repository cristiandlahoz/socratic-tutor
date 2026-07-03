package com.wornux.security.authorization;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.wornux.data.repositories.authorization.RoleNamespaceRepository;
import com.wornux.data.repositories.authorization.RoleRepository;
import com.wornux.security.permission.AppPermission;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final RoleNamespaceRepository roleNamespaceRepository;
    private final AuthorizationService authorizationService;
    private final ApplicationEventPublisher eventPublisher;

    public RoleManagementService(
            RoleRepository roleRepository,
            RoleNamespaceRepository roleNamespaceRepository,
            AuthorizationService authorizationService,
            ApplicationEventPublisher eventPublisher) {
        this.roleRepository = roleRepository;
        this.roleNamespaceRepository = roleNamespaceRepository;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void updatePermissions(UUID roleId, Set<String> permissionCodes) {
        authorizationService.check(AppPermission.ROLE_UPDATE);
        var actor = authorizationService.snapshot();
        if (!actor.permissionCodes().containsAll(permissionCodes)) {
            throw new AccessDeniedException("Cannot grant permissions outside the actor snapshot");
        }
        var role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role %s".formatted(roleId)));
        enforcePriorityBoundary(actor, role.getRoleNamespace().getId(), role.getPriority());
        role.setPermissions(permissionCodes.stream().sorted().toArray(String[]::new));
        role.setUpdatedAt(Instant.now());
        roleRepository.save(role);
        changed(role.getRoleNamespace().getId());
    }

    @Transactional(readOnly = true)
    public boolean actorCanGrant(Set<String> permissionCodes) {
        return authorizationService.snapshot().permissionCodes().containsAll(permissionCodes);
    }

    private void enforcePriorityBoundary(
            UserAccessSnapshot actor,
            UUID roleNamespaceId,
            int targetPriority) {
        var actorHighestPriority = roleRepository.findByRoleNamespace_IdAndActiveTrue(roleNamespaceId).stream()
                .filter(role -> actor.roleCodes().contains(role.getCode()))
                .mapToInt(role -> role.getPriority())
                .max()
                .orElse(-1);
        if (targetPriority >= actorHighestPriority) {
            throw new AccessDeniedException("Cannot manage a role at or above the actor priority");
        }
    }

    private void changed(UUID roleNamespaceId) {
        roleNamespaceRepository.incrementRbacVersion(roleNamespaceId);
        eventPublisher.publishEvent(new RbacChangedEvent(roleNamespaceId));
    }
}
