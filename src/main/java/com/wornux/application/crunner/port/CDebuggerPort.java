package com.wornux.application.crunner.port;

import com.wornux.application.crunner.CDebugSessionResult;
import com.wornux.application.crunner.CSourceRequest;

public interface CDebuggerPort {

  String cacheKey();

  CDebugSessionResult debug(CSourceRequest request, String sourceHash);
}
