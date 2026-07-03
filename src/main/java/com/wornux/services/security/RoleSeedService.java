package com.wornux.services.security;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.authorization.Role;
import com.wornux.data.entities.authorization.RoleAssignmentLevel;
import com.wornux.data.entities.authorization.RoleNamespace;
import com.wornux.data.repositories.authorization.RoleRepository;
import com.wornux.security.permission.AppPermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleSeedService {

    private final RoleRepository roleRepository;

    public RoleSeedService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional
    public void seedTenantDefaultRoles(RoleNamespace namespace) {
        seed(namespace, "TENANT_ADMIN", "Tenant Admin", "Tenant-scoped academic administrator.",
                RoleAssignmentLevel.TENANT, 80, false, true, tenantAdminPermissions());
        seed(namespace, "PROFESSOR", "Professor", "Professor operating inside assigned group classes.",
                RoleAssignmentLevel.GROUP_CLASS, 60, true, true, professorPermissions());
        seed(namespace, "STUDENT", "Student", "Student operating only inside owned group-class scope.",
                RoleAssignmentLevel.GROUP_CLASS, 40, true, true, studentPermissions());
    }

    private void seed(
            RoleNamespace namespace,
            String code,
            String name,
            String description,
            RoleAssignmentLevel level,
            int priority,
            boolean assignable,
            boolean systemDefined,
            String[] permissions) {
        if (roleRepository.findByRoleNamespace_IdAndCode(namespace.getId(), code).isPresent()) {
            return;
        }
        var now = Instant.now();
        var role = new Role();
        role.setId(UUID.randomUUID());
        role.setRoleNamespace(namespace);
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        role.setAssignmentLevel(level);
        role.setPermissions(permissions);
        role.setPriority(priority);
        role.setAssignable(assignable);
        role.setSystemDefined(systemDefined);
        role.setActive(true);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        roleRepository.save(role);
    }

    private String[] tenantAdminPermissions() {
        return codes(
                AppPermission.ROLE_VIEW, AppPermission.ROLE_CREATE, AppPermission.ROLE_UPDATE, AppPermission.ROLE_ASSIGN,
                AppPermission.SUBJECT_VIEW, AppPermission.SUBJECT_CREATE, AppPermission.SUBJECT_UPDATE,
                AppPermission.SUBJECT_DELETE, AppPermission.ACADEMIC_PERIOD_VIEW, AppPermission.ACADEMIC_PERIOD_CREATE,
                AppPermission.ACADEMIC_PERIOD_UPDATE, AppPermission.ACADEMIC_PERIOD_DELETE, AppPermission.GROUP_CLASS_VIEW,
                AppPermission.GROUP_CLASS_CREATE, AppPermission.GROUP_CLASS_UPDATE, AppPermission.GROUP_CLASS_DELETE,
                AppPermission.GROUP_CLASS_MEMBER_VIEW, AppPermission.GROUP_CLASS_MEMBER_INVITE,
                AppPermission.GROUP_CLASS_MEMBER_UPDATE, AppPermission.GROUP_CLASS_MEMBER_DELETE,
                AppPermission.GROUP_CLASS_JOIN_CODE_VIEW, AppPermission.GROUP_CLASS_JOIN_CODE_CREATE,
                AppPermission.GROUP_CLASS_JOIN_CODE_UPDATE, AppPermission.GROUP_CLASS_JOIN_CODE_DELETE,
                AppPermission.TRAINING_ACTIVITY_VIEW,
                AppPermission.TRAINING_ACTIVITY_CREATE, AppPermission.TRAINING_ACTIVITY_UPDATE,
                AppPermission.TRAINING_ACTIVITY_DELETE, AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW,
                AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_CREATE, AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_UPDATE,
                AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_DELETE, AppPermission.COURSE_MATERIAL_VIEW,
                AppPermission.COURSE_MATERIAL_CREATE, AppPermission.COURSE_MATERIAL_UPDATE,
                AppPermission.COURSE_MATERIAL_DELETE, AppPermission.CONVERSATION_VIEW);
    }

    private String[] professorPermissions() {
        return codes(AppPermission.GROUP_CLASS_VIEW, AppPermission.GROUP_CLASS_UPDATE,
                AppPermission.GROUP_CLASS_MEMBER_VIEW, AppPermission.GROUP_CLASS_MEMBER_INVITE,
                AppPermission.GROUP_CLASS_MEMBER_UPDATE, AppPermission.GROUP_CLASS_JOIN_CODE_VIEW,
                AppPermission.GROUP_CLASS_JOIN_CODE_CREATE, AppPermission.GROUP_CLASS_JOIN_CODE_UPDATE,
                AppPermission.GROUP_CLASS_JOIN_CODE_DELETE, AppPermission.TRAINING_ACTIVITY_VIEW,
                AppPermission.TRAINING_ACTIVITY_CREATE, AppPermission.TRAINING_ACTIVITY_UPDATE,
                AppPermission.TRAINING_ACTIVITY_DELETE, AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW,
                AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_CREATE, AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_UPDATE,
                AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_DELETE, AppPermission.COURSE_MATERIAL_VIEW,
                AppPermission.COURSE_MATERIAL_CREATE, AppPermission.COURSE_MATERIAL_UPDATE,
                AppPermission.COURSE_MATERIAL_DELETE, AppPermission.CONVERSATION_VIEW);
    }

    private String[] studentPermissions() {
        return codes(AppPermission.GROUP_CLASS_VIEW, AppPermission.CONVERSATION_VIEW, AppPermission.CONVERSATION_CREATE,
                AppPermission.CONVERSATION_UPDATE, AppPermission.CONVERSATION_DELETE, AppPermission.TRAINING_ACTIVITY_VIEW,
                AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW, AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_UPDATE);
    }

    private String[] codes(AppPermission... permissions) {
        return java.util.Arrays.stream(permissions).map(AppPermission::code).toArray(String[]::new);
    }
}
