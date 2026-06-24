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
public class EvaluationPromptResources {

    private static final String ADAPTIVE_PROMPT = "classpath:/evaluation/adaptive-prompt.st";
    private static final String FALLBACK_QUESTION = "classpath:/evaluation/fallback-question.st";
    private static final String REPORT_PROMPT = "classpath:/evaluation/report-prompt.st";
    private static final String ANTI_LOOP_BLOCKED = "classpath:/evaluation/anti-loop-blocked.st";

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public EvaluationPromptResources(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
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
        Resource resource = resourceLoader.getResource(location);
        try (var inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new UncheckedIOException("Failed to read evaluation prompt resource %s".formatted(location),
                    exception);
        }
    }
}
