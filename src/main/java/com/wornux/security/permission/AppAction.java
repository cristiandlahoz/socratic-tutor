package com.wornux.security.permission;

public enum AppAction {
    VIEW("view"),
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    ASSIGN("assign"),
    INVITE("invite"),
    LOCK("lock"),
    EXPORT("export");

    private final String code;

    AppAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean grants(AppAction requested) {
        return this == requested || requested == VIEW;
    }
}
