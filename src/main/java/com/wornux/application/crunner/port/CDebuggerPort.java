package com.wornux.application.crunner.port;

import com.wornux.application.crunner.CDebugSessionResult;
import com.wornux.application.crunner.CDebugRequest;

public interface CDebuggerPort {

  String cacheKey();

  CDebugSessionResult debug(CDebugRequest request, String sourceHash);
}
