package com.wornux.security.permission;

public enum AppResource {
    TENANT("tenant"),
    ACCOUNT("account"),
    ROLE("role"),
    SUBJECT("subject"),
    ACADEMIC_PERIOD("academic-period"),
    GROUP_CLASS("group-class"),
    GROUP_CLASS_MEMBER("group-class-member"),
    GROUP_CLASS_JOIN_CODE("group-class-join-code"),
    CONVERSATION("conversation"),
    TRAINING_ACTIVITY("training-activity"),
    TRAINING_ACTIVITY_ASSIGNMENT("training-activity-assignment"),
    COURSE_MATERIAL("course-material");

    private final String code;

    AppResource(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
