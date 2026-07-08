package com.wornux.services.crunner;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wornux.config.ApplicationProperties;
import com.wornux.infrastructure.external.crunner.DockerGccCCompilerAdapter;
import com.wornux.util.Sha256;
import org.springframework.stereotype.Service;

@Service
public class CProgramAnalysisService {

    private static final String SUPPORTED_STANDARD = "c17";

    private final DockerGccCCompilerAdapter compiler;
    private final ApplicationProperties.CRunner properties;
    private final Cache<CValidationCacheKey, CValidationResult> cache;

    public CProgramAnalysisService(DockerGccCCompilerAdapter compiler, ApplicationProperties.CRunner properties) {
        this.compiler = compiler;
        this.properties = properties;
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1, properties.getCacheMaximumSize()))
                .expireAfterWrite(properties.getCacheTtl())
                .build();
    }

    public CValidationResult validateSyntax(CSourceRequest request) {
        var normalizedRequest = request == null ? new CSourceRequest("", null, null) : request;
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

        var cacheKey = new CValidationCacheKey(sourceHash,
                normalizedRequest.standard(),
                normalizedRequest.filename(),
                compiler.cacheKey());
        return cache.get(cacheKey, ignored -> compiler.validateSyntax(normalizedRequest, sourceHash));
    }

    private static CValidationResult rejected(String sourceHash, String message, String ruleId) {
        return new CValidationResult(false, List.of(CDiagnostic.error(message, ruleId)), "not-run", 0, sourceHash);
    }

    private record CValidationCacheKey(String sourceHash, String standard, String filename, String compilerCacheKey) {}
}
