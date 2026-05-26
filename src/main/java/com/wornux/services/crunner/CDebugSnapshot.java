package com.wornux.services.crunner;

import java.util.List;

public record CDebugSnapshot(
    int index,
    Integer line,
    String functionName,
    String stdout,
    List<CDebugVariable> locals,
    boolean terminated,
    String reason) {

  public CDebugSnapshot {
    functionName = functionName == null ? "" : functionName;
    stdout = stdout == null ? "" : stdout;
    locals = locals == null ? List.of() : List.copyOf(locals);
    reason = reason == null ? "" : reason;
  }
}
