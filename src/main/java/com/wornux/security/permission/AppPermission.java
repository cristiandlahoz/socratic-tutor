package com.wornux.security.permission;

public enum AppPermission {
    TENANT_VIEW(AppResource.TENANT, AppAction.VIEW, "tenant:view"),
    TENANT_CREATE(AppResource.TENANT, AppAction.CREATE, "tenant:create"),
    TENANT_UPDATE(AppResource.TENANT, AppAction.UPDATE, "tenant:update"),
    ACCOUNT_VIEW(AppResource.ACCOUNT, AppAction.VIEW, "account:view"),
    ACCOUNT_UPDATE(AppResource.ACCOUNT, AppAction.UPDATE, "account:update"),
    ROLE_VIEW(AppResource.ROLE, AppAction.VIEW, "role:view"),
    ROLE_CREATE(AppResource.ROLE, AppAction.CREATE, "role:create"),
    ROLE_UPDATE(AppResource.ROLE, AppAction.UPDATE, "role:update"),
    ROLE_DELETE(AppResource.ROLE, AppAction.DELETE, "role:delete"),
    ROLE_ASSIGN(AppResource.ROLE, AppAction.ASSIGN, "role:assign"),
    SUBJECT_VIEW(AppResource.SUBJECT, AppAction.VIEW, "subject:view"),
    SUBJECT_CREATE(AppResource.SUBJECT, AppAction.CREATE, "subject:create"),
    SUBJECT_UPDATE(AppResource.SUBJECT, AppAction.UPDATE, "subject:update"),
    SUBJECT_DELETE(AppResource.SUBJECT, AppAction.DELETE, "subject:delete"),
    ACADEMIC_PERIOD_VIEW(AppResource.ACADEMIC_PERIOD, AppAction.VIEW, "academic-period:view"),
    ACADEMIC_PERIOD_CREATE(AppResource.ACADEMIC_PERIOD, AppAction.CREATE, "academic-period:create"),
    ACADEMIC_PERIOD_UPDATE(AppResource.ACADEMIC_PERIOD, AppAction.UPDATE, "academic-period:update"),
    ACADEMIC_PERIOD_DELETE(AppResource.ACADEMIC_PERIOD, AppAction.DELETE, "academic-period:delete"),
    GROUP_CLASS_VIEW(AppResource.GROUP_CLASS, AppAction.VIEW, "group-class:view"),
    GROUP_CLASS_CREATE(AppResource.GROUP_CLASS, AppAction.CREATE, "group-class:create"),
    GROUP_CLASS_UPDATE(AppResource.GROUP_CLASS, AppAction.UPDATE, "group-class:update"),
    GROUP_CLASS_DELETE(AppResource.GROUP_CLASS, AppAction.DELETE, "group-class:delete"),
    GROUP_CLASS_MEMBER_VIEW(AppResource.GROUP_CLASS_MEMBER, AppAction.VIEW, "group-class-member:view"),
    GROUP_CLASS_MEMBER_CREATE(AppResource.GROUP_CLASS_MEMBER, AppAction.CREATE, "group-class-member:create"),
    GROUP_CLASS_MEMBER_UPDATE(AppResource.GROUP_CLASS_MEMBER, AppAction.UPDATE, "group-class-member:update"),
    GROUP_CLASS_MEMBER_DELETE(AppResource.GROUP_CLASS_MEMBER, AppAction.DELETE, "group-class-member:delete"),
    GROUP_CLASS_MEMBER_INVITE(AppResource.GROUP_CLASS_MEMBER, AppAction.INVITE, "group-class-member:invite"),
    GROUP_CLASS_JOIN_CODE_VIEW(AppResource.GROUP_CLASS_JOIN_CODE, AppAction.VIEW, "group-class-join-code:view"),
    GROUP_CLASS_JOIN_CODE_CREATE(AppResource.GROUP_CLASS_JOIN_CODE, AppAction.CREATE, "group-class-join-code:create"),
    GROUP_CLASS_JOIN_CODE_UPDATE(AppResource.GROUP_CLASS_JOIN_CODE, AppAction.UPDATE, "group-class-join-code:update"),
    GROUP_CLASS_JOIN_CODE_DELETE(AppResource.GROUP_CLASS_JOIN_CODE, AppAction.DELETE, "group-class-join-code:delete"),
    CONVERSATION_VIEW(AppResource.CONVERSATION, AppAction.VIEW, "conversation:view"),
    CONVERSATION_CREATE(AppResource.CONVERSATION, AppAction.CREATE, "conversation:create"),
    CONVERSATION_UPDATE(AppResource.CONVERSATION, AppAction.UPDATE, "conversation:update"),
    CONVERSATION_DELETE(AppResource.CONVERSATION, AppAction.DELETE, "conversation:delete"),
    TRAINING_ACTIVITY_VIEW(AppResource.TRAINING_ACTIVITY, AppAction.VIEW, "training-activity:view"),
    TRAINING_ACTIVITY_CREATE(AppResource.TRAINING_ACTIVITY, AppAction.CREATE, "training-activity:create"),
    TRAINING_ACTIVITY_UPDATE(AppResource.TRAINING_ACTIVITY, AppAction.UPDATE, "training-activity:update"),
    TRAINING_ACTIVITY_DELETE(AppResource.TRAINING_ACTIVITY, AppAction.DELETE, "training-activity:delete"),
    TRAINING_ACTIVITY_ASSIGNMENT_VIEW(AppResource.TRAINING_ACTIVITY_ASSIGNMENT, AppAction.VIEW, "training-activity-assignment:view"),
    TRAINING_ACTIVITY_ASSIGNMENT_CREATE(AppResource.TRAINING_ACTIVITY_ASSIGNMENT, AppAction.CREATE, "training-activity-assignment:create"),
    TRAINING_ACTIVITY_ASSIGNMENT_UPDATE(AppResource.TRAINING_ACTIVITY_ASSIGNMENT, AppAction.UPDATE, "training-activity-assignment:update"),
    TRAINING_ACTIVITY_ASSIGNMENT_DELETE(AppResource.TRAINING_ACTIVITY_ASSIGNMENT, AppAction.DELETE, "training-activity-assignment:delete"),
    COURSE_MATERIAL_VIEW(AppResource.COURSE_MATERIAL, AppAction.VIEW, "course-material:view"),
    COURSE_MATERIAL_CREATE(AppResource.COURSE_MATERIAL, AppAction.CREATE, "course-material:create"),
    COURSE_MATERIAL_UPDATE(AppResource.COURSE_MATERIAL, AppAction.UPDATE, "course-material:update"),
    COURSE_MATERIAL_DELETE(AppResource.COURSE_MATERIAL, AppAction.DELETE, "course-material:delete");

    private final AppResource resource;
    private final AppAction action;
    private final String code;

    AppPermission(AppResource resource, AppAction action, String code) {
        this.resource = resource;
        this.action = action;
        this.code = code;
    }

    public AppResource resource() { return resource; }

    public AppAction action() { return action; }

    public String code() { return code; }
}
