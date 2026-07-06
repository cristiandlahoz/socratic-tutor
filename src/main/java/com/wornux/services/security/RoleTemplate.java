package com.wornux.services.security;

import java.util.Arrays;

import com.wornux.data.entities.authorization.RoleAssignmentLevel;
import com.wornux.security.permission.AppPermission;

public enum RoleTemplate {
    SYSTEM_ADMIN(
            "SYSTEM_ADMIN",
            "System Admin",
            "Platform-level administrator with full visibility.",
            RoleAssignmentLevel.PLATFORM,
            100,
            false,
            true,
            AppPermission.TENANT_VIEW,
            AppPermission.TENANT_CREATE,
            AppPermission.TENANT_UPDATE,
            AppPermission.ACCOUNT_VIEW,
            AppPermission.ACCOUNT_UPDATE,
            AppPermission.ROLE_VIEW,
            AppPermission.ROLE_CREATE,
            AppPermission.ROLE_UPDATE,
            AppPermission.ROLE_DELETE,
            AppPermission.ROLE_ASSIGN),
    TENANT_ADMIN(
            "TENANT_ADMIN",
            "Tenant Admin",
            "Tenant-scoped academic administrator.",
            RoleAssignmentLevel.TENANT,
            80,
            true,
            true,
            AppPermission.ROLE_VIEW,
            AppPermission.ROLE_CREATE,
            AppPermission.ROLE_UPDATE,
            AppPermission.ROLE_ASSIGN,
            AppPermission.SUBJECT_VIEW,
            AppPermission.SUBJECT_CREATE,
            AppPermission.SUBJECT_UPDATE,
            AppPermission.SUBJECT_DELETE,
            AppPermission.ACADEMIC_PERIOD_VIEW,
            AppPermission.ACADEMIC_PERIOD_CREATE,
            AppPermission.ACADEMIC_PERIOD_UPDATE,
            AppPermission.ACADEMIC_PERIOD_DELETE,
            AppPermission.GROUP_CLASS_VIEW,
            AppPermission.GROUP_CLASS_CREATE,
            AppPermission.GROUP_CLASS_UPDATE,
            AppPermission.GROUP_CLASS_DELETE,
            AppPermission.GROUP_CLASS_MEMBER_VIEW,
            AppPermission.GROUP_CLASS_MEMBER_INVITE,
            AppPermission.GROUP_CLASS_MEMBER_UPDATE,
            AppPermission.GROUP_CLASS_MEMBER_DELETE,
            AppPermission.GROUP_CLASS_JOIN_CODE_VIEW,
            AppPermission.GROUP_CLASS_JOIN_CODE_CREATE,
            AppPermission.GROUP_CLASS_JOIN_CODE_UPDATE,
            AppPermission.GROUP_CLASS_JOIN_CODE_DELETE,
            AppPermission.TRAINING_ACTIVITY_VIEW,
            AppPermission.TRAINING_ACTIVITY_CREATE,
            AppPermission.TRAINING_ACTIVITY_UPDATE,
            AppPermission.TRAINING_ACTIVITY_DELETE,
            AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW,
            AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_CREATE,
            AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_UPDATE,
            AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_DELETE,
            AppPermission.COURSE_MATERIAL_VIEW,
            AppPermission.COURSE_MATERIAL_CREATE,
            AppPermission.COURSE_MATERIAL_UPDATE,
            AppPermission.COURSE_MATERIAL_DELETE,
            AppPermission.CONVERSATION_VIEW),
    PROFESSOR(
            "PROFESSOR",
            "Professor",
            "Professor operating inside assigned group classes.",
            RoleAssignmentLevel.GROUP_CLASS,
            60,
            true,
            true,
            AppPermission.ROLE_VIEW,
            AppPermission.ROLE_UPDATE,
            AppPermission.ROLE_ASSIGN,
            AppPermission.GROUP_CLASS_VIEW,
            AppPermission.GROUP_CLASS_UPDATE,
            AppPermission.GROUP_CLASS_MEMBER_VIEW,
            AppPermission.GROUP_CLASS_MEMBER_INVITE,
            AppPermission.GROUP_CLASS_MEMBER_UPDATE,
            AppPermission.GROUP_CLASS_JOIN_CODE_VIEW,
            AppPermission.GROUP_CLASS_JOIN_CODE_CREATE,
            AppPermission.GROUP_CLASS_JOIN_CODE_UPDATE,
            AppPermission.GROUP_CLASS_JOIN_CODE_DELETE,
            AppPermission.TRAINING_ACTIVITY_VIEW,
            AppPermission.TRAINING_ACTIVITY_CREATE,
            AppPermission.TRAINING_ACTIVITY_UPDATE,
            AppPermission.TRAINING_ACTIVITY_DELETE,
            AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW,
            AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_CREATE,
            AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_UPDATE,
            AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_DELETE,
            AppPermission.COURSE_MATERIAL_VIEW,
            AppPermission.COURSE_MATERIAL_CREATE,
            AppPermission.COURSE_MATERIAL_UPDATE,
            AppPermission.COURSE_MATERIAL_DELETE,
            AppPermission.CONVERSATION_VIEW),
    STUDENT(
            "STUDENT",
            "Student",
            "Student operating only inside owned group-class scope.",
            RoleAssignmentLevel.GROUP_CLASS,
            40,
            true,
            true,
            AppPermission.GROUP_CLASS_VIEW,
            AppPermission.CONVERSATION_VIEW,
            AppPermission.CONVERSATION_CREATE,
            AppPermission.CONVERSATION_UPDATE,
            AppPermission.CONVERSATION_DELETE,
            AppPermission.TRAINING_ACTIVITY_VIEW,
            AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW,
            AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_UPDATE);

    private final String code;
    private final String displayName;
    private final String description;
    private final RoleAssignmentLevel assignmentLevel;
    private final int priority;
    private final boolean assignable;
    private final boolean systemDefined;
    private final AppPermission[] permissions;

    RoleTemplate(
            String code,
            String displayName,
            String description,
            RoleAssignmentLevel assignmentLevel,
            int priority,
            boolean assignable,
            boolean systemDefined,
            AppPermission... permissions) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.assignmentLevel = assignmentLevel;
        this.priority = priority;
        this.assignable = assignable;
        this.systemDefined = systemDefined;
        this.permissions = permissions;
    }

    public String code() { return code; }

    public String displayName() { return displayName; }

    public String description() { return description; }

    public RoleAssignmentLevel assignmentLevel() { return assignmentLevel; }

    public int priority() { return priority; }

    public boolean assignable() { return assignable; }

    public boolean systemDefined() { return systemDefined; }

    public String[] permissionCodes() {
        return Arrays.stream(permissions).map(AppPermission::code).sorted().toArray(String[]::new);
    }
}
