package com.wornux.services.crunner;

public record CDebugVariable(String name, String value, String scope) {

    public CDebugVariable {
        name = name == null ? "" : name;
        value = value == null ? "" : value;
        scope = scope == null || scope.isBlank() ? "local" : scope;
    }
}
