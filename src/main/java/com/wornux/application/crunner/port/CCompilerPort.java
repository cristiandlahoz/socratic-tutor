package com.wornux.application.crunner.port;

import com.wornux.application.crunner.CSourceRequest;
import com.wornux.application.crunner.CValidationResult;

public interface CCompilerPort {

  String cacheKey();

  CValidationResult validateSyntax(CSourceRequest request, String sourceHash);
}
