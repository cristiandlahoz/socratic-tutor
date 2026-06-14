package com.wornux.services.crunner;

import java.util.List;

public record CValidationResult(boolean valid, List<CDiagnostic> diagnostics, String compiler, long elapsedMs,
        String sourceHash) {

    public CValidationResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        compiler = compiler == null ? "" : compiler;
        sourceHash = sourceHash == null ? "" : sourceHash;
    }
}
