package com.wornux.application.crunner;

import java.util.List;

public record CDebugSessionResult(
    boolean valid,
    List<CDiagnostic> diagnostics,
    List<CDebugSnapshot> snapshots,
    String compiler,
    long elapsedMs,
    String sourceHash) {

  public CDebugSessionResult {
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
    compiler = compiler == null ? "" : compiler;
    sourceHash = sourceHash == null ? "" : sourceHash;
  }
}
