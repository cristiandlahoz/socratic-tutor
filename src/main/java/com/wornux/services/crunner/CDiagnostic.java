package com.wornux.services.crunner;

public record CDiagnostic(CDiagnosticSeverity severity, String message, Integer line, Integer column, Integer endLine,
        Integer endColumn, Integer fromOffset, Integer toOffset, String ruleId) {

    public CDiagnostic {
        severity = severity == null ? CDiagnosticSeverity.INFO : severity;
        message = message == null ? "" : message;
    }

    public static CDiagnostic error(String message, String ruleId) {
        return new CDiagnostic(CDiagnosticSeverity.ERROR, message, null, null, null, null, null, null, ruleId);
    }
}
