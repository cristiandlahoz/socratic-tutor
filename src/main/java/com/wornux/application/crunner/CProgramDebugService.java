package com.wornux.application.crunner;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wornux.application.crunner.port.CDebuggerPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CProgramDebugService {

  private static final String SUPPORTED_STANDARD = "c17";

  private final CDebuggerPort debuggerPort;
  private final CProgramAnalysisProperties properties;
  private final Cache<CDebugCacheKey, CDebugSessionResult> cache;

  public CProgramDebugService(CDebuggerPort debuggerPort, CProgramAnalysisProperties properties) {
    this.debuggerPort = debuggerPort;
    this.properties = properties;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(Math.max(1, properties.getCacheMaximumSize()))
            .expireAfterWrite(properties.getCacheTtl())
            .build();
  }

  public CDebugSessionResult debug(CSourceRequest request) {
    return debug(CDebugRequest.from(request, ""));
  }

  public CDebugSessionResult debug(CDebugRequest request) {
    var normalizedRequest = request == null ? new CDebugRequest("", null, null, "") : request;
    var sourceHash = hash(normalizedRequest.source());
    if (!SUPPORTED_STANDARD.equals(normalizedRequest.standard())) {
      return rejected(
          sourceHash, "Unsupported C standard: " + normalizedRequest.standard(), "unsupported-standard");
    }
    var sourceBytes = normalizedRequest.source().getBytes(StandardCharsets.UTF_8).length;
    if (sourceBytes > properties.getMaxSourceBytes()) {
      return rejected(
          sourceHash,
          "Source is too large: %d bytes exceeds the %d byte limit"
              .formatted(sourceBytes, properties.getMaxSourceBytes()),
          "source-too-large");
    }

    var cacheKey =
        new CDebugCacheKey(
            sourceHash,
            hash(normalizedRequest.stdin()),
            normalizedRequest.standard(),
            normalizedRequest.filename(),
            debuggerPort.cacheKey());
    return cache.get(cacheKey, ignored -> debuggerPort.debug(normalizedRequest, sourceHash));
  }

  private static CDebugSessionResult rejected(String sourceHash, String message, String ruleId) {
    return new CDebugSessionResult(
        false, List.of(CDiagnostic.error(message, ruleId)), List.of(), "not-run", 0, sourceHash);
  }

  private static String hash(String source) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private record CDebugCacheKey(
      String sourceHash, String stdinHash, String standard, String filename, String debuggerCacheKey) {}
}
