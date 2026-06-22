package com.wornux.services.workspace;

public enum WorkspaceDestination {
    SYSTEM_ADMIN("admin"),
    TENANT_ADMIN("tenant"),
    PROFESSOR("professor"),
    STUDENT("student"),
    NO_ACCESS("no-access");

    private final String route;

    WorkspaceDestination(String route) {
        this.route = route;
    }

    public String route() {
        return route;
    }
}
