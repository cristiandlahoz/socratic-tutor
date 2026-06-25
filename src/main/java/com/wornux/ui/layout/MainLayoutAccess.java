package com.wornux.ui.layout;

public record MainLayoutAccess(
        boolean systemAdmin,
        boolean tenantAdmin,
        boolean professor,
        boolean student) {

    public static MainLayoutAccess none() {
        return new MainLayoutAccess(false, false, false, false);
    }

    public boolean canChat() {
        return professor || student;
    }

    public boolean canManageDocuments() {
        return professor;
    }

    public boolean canManageActivities() {
        return professor;
    }
}
