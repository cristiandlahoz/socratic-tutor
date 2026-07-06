package com.wornux.services.security;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.authorization.Role;
import com.wornux.data.entities.authorization.RoleNamespace;
import com.wornux.data.repositories.authorization.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleTemplateSeeder {

    private final RoleRepository roleRepository;
    private final RoleNamespaceService roleNamespaceService;

    public RoleTemplateSeeder(RoleRepository roleRepository, RoleNamespaceService roleNamespaceService) {
        this.roleRepository = roleRepository;
        this.roleNamespaceService = roleNamespaceService;
    }

    @Transactional
    public Role ensureRole(RoleNamespace namespace, RoleTemplate template) {
        return roleRepository.findByRoleNamespace_IdAndCode(namespace.getId(), template.code())
                .orElseGet(() -> createRole(namespace, template));
    }

    private Role createRole(RoleNamespace namespace, RoleTemplate template) {
        var now = Instant.now();
        var role = new Role();
        role.setId(UUID.randomUUID());
        role.setRoleNamespace(namespace);
        role.setCode(template.code());
        role.setName(template.displayName());
        role.setDescription(template.description());
        role.setAssignmentLevel(template.assignmentLevel());
        role.setPermissions(template.permissionCodes());
        role.setPriority(template.priority());
        role.setAssignable(template.assignable());
        role.setSystemDefined(template.systemDefined());
        role.setActive(true);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        var saved = roleRepository.save(role);
        roleNamespaceService.recordRbacChange(namespace.getId());
        return saved;
    }
}
