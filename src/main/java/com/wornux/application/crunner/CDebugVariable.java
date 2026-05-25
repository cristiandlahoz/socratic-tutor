package com.wornux.application.crunner;

public record CDebugVariable(String type, String name, String value, String scope) {

    public CDebugVariable {
        type = type == null || type.isBlank() ? "?" : type;
        name = name == null ? "" : name;
        value = value == null ? "" : value;
        scope = scope == null || scope.isBlank() ? "local" : scope;
    }
}
