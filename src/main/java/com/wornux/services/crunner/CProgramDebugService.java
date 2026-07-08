package com.wornux.services.crunner;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wornux.config.CProgramAnalysisProperties;
import com.wornux.infrastructure.external.crunner.DockerGdbCDebuggerAdapter;
import com.wornux.util.Sha256;
import org.springframework.stereotype.Service;

@Service
public class CProgramDebugService {

    private static final String SUPPORTED_STANDARD = "c17";

    private final DockerGdbCDebuggerAdapter debugger;
    private final CProgramAnalysisProperties properties;
    private final Cache<CDebugCacheKey, CDebugSessionResult> cache;

    public CProgramDebugService(DockerGdbCDebuggerAdapter debugger, CProgramAnalysisProperties properties) {
        this.debugger = debugger;
        this.properties = properties;
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1, properties.getCacheMaximumSize()))
                .expireAfterWrite(properties.getCacheTtl())
                .build();
    }

    public CDebugSessionResult debug(CSourceRequest request) {
        return debug(CDebugRequest.from(request, ""));
    }

    public CDebugSessionResult debug(CDebugRequest request) {
        var normalizedRequest = request == null ? new CDebugRequest("", null, null, "") : request;
        var sourceHash = Sha256.hex(normalizedRequest.source());
        if (!SUPPORTED_STANDARD.equals(normalizedRequest.standard())) {
            return rejected(
                sourceHash,
                "Unsupported C standard: %s".formatted(normalizedRequest.standard()),
                "unsupported-standard");
        }
        var sourceBytes = normalizedRequest.source().getBytes(StandardCharsets.UTF_8).length;
        if (sourceBytes > properties.getMaxSourceBytes()) {
            return rejected(
                sourceHash,
                "Source is too large: %d bytes exceeds the %d byte limit"
                        .formatted(sourceBytes, properties.getMaxSourceBytes()),
                "source-too-large");
        }

        var cacheKey = new CDebugCacheKey(sourceHash,
                Sha256.hex(normalizedRequest.stdin()),
                normalizedRequest.standard(),
                normalizedRequest.filename(),
                debugger.cacheKey());
        return cache.get(cacheKey, ignored -> debugger.debug(normalizedRequest, sourceHash));
    }

    private static CDebugSessionResult rejected(String sourceHash, String message, String ruleId) {
        return new CDebugSessionResult(false,
                List.of(CDiagnostic.error(message, ruleId)),
                List.of(),
                "not-run",
                0,
                sourceHash);
    }

    private record CDebugCacheKey(String sourceHash, String stdinHash, String standard, String filename,
            String debuggerCacheKey) {}
}
