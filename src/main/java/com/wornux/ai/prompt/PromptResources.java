package com.wornux.ai.prompt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class PromptResources {

    private static final String BASE_IDENTITY_SYSTEM = "classpath:/prompt/tutor/base-identity-system.st";
    private static final String GUARD_NOT_SAFE = "classpath:/prompt/tutor/guardrail/guard-not-safe.st";
    private static final String GUARD_IMPERSONATION = "classpath:/prompt/tutor/guardrail/guard-impersonation.st";
    private static final String GUARD_OUT_OF_SCOPE = "classpath:/prompt/tutor/guardrail/guard-out-of-scope.st";
    private static final String GUARD_CLASSIFIER = "classpath:/prompt/tutor/guardrail/guard-classifier.st";
    private static final String ADAPTIVE_PROMPT = "classpath:/prompt/training_activity/adaptive-prompt.st";
    private static final String FALLBACK_QUESTION = "classpath:/prompt/training_activity/fallback-question.st";
    private static final String REPORT_PROMPT = "classpath:/prompt/training_activity/report-prompt.st";
    private static final String ANTI_LOOP_BLOCKED = "classpath:/prompt/training_activity/anti-loop-blocked.st";

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public PromptResources(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public Resource baseIdentitySystemResource() {
        return resource(BASE_IDENTITY_SYSTEM);
    }

    public String guardNotSafe() {
        return load(GUARD_NOT_SAFE);
    }

    public String guardImpersonation() {
        return load(GUARD_IMPERSONATION);
    }

    public String guardOutOfScope() {
        return load(GUARD_OUT_OF_SCOPE);
    }

    public String guardClassifier() {
        return load(GUARD_CLASSIFIER);
    }

    public String adaptivePrompt() {
        return load(ADAPTIVE_PROMPT);
    }

    public String fallbackQuestion() {
        return load(FALLBACK_QUESTION);
    }

    public String reportPrompt() {
        return load(REPORT_PROMPT);
    }

    public String antiLoopBlocked() {
        return load(ANTI_LOOP_BLOCKED);
    }

    private String load(String location) {
        return cache.computeIfAbsent(location, this::read);
    }

    private String read(String location) {
        Resource resource = resource(location);
        try (var inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new UncheckedIOException("Failed to read prompt resource %s".formatted(location), exception);
        }
    }

    private Resource resource(String location) {
        return resourceLoader.getResource(location);
    }
}
