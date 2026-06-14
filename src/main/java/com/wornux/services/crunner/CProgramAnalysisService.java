package com.wornux.services.crunner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wornux.config.CProgramAnalysisProperties;
import com.wornux.infrastructure.external.crunner.DockerGccCCompilerAdapter;
import org.springframework.stereotype.Service;

@Service
public class CProgramAnalysisService {

    private static final String SUPPORTED_STANDARD = "c17";

    private final DockerGccCCompilerAdapter compiler;
    private final CProgramAnalysisProperties properties;
    private final Cache<CValidationCacheKey, CValidationResult> cache;

    public CProgramAnalysisService(DockerGccCCompilerAdapter compiler, CProgramAnalysisProperties properties) {
        this.compiler = compiler;
        this.properties = properties;
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1, properties.getCacheMaximumSize()))
                .expireAfterWrite(properties.getCacheTtl())
                .build();
    }

    public CValidationResult validateSyntax(CSourceRequest request) {
        var normalizedRequest = request == null ? new CSourceRequest("", null, null) : request;
        var sourceHash = hash(normalizedRequest.source());
        if (!SUPPORTED_STANDARD.equals(normalizedRequest.standard())) {
            return rejected(
                sourceHash,
                "Unsupported C standard: " + normalizedRequest.standard(),
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

    private static String hash(String source) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record CValidationCacheKey(String sourceHash, String standard, String filename, String compilerCacheKey) {}
}
