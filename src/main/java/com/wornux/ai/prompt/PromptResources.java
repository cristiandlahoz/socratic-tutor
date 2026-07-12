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
    private static final String GUARD_CLASSIFIER = "classpath:/prompt/tutor/guardrail/guard-classifier.st";
    private static final String COMPACTION_SYSTEM = "classpath:/prompt/compaction/system.st";
    private static final String COMPACTION_USER = "classpath:/prompt/compaction/user.st";
    private static final String ADAPTIVE_TUTOR_SYSTEM = "classpath:/prompt/training_activity/adaptive-tutor-system.st";
    private static final String ADAPTIVE_PROMPT = "classpath:/prompt/training_activity/adaptive-prompt.st";
    private static final String FALLBACK_QUESTION = "classpath:/prompt/training_activity/fallback-question.st";
    private static final String REPORT_PROMPT = "classpath:/prompt/training_activity/report-prompt.st";
    private static final String ANTI_LOOP_BLOCKED = "classpath:/prompt/training_activity/anti-loop-blocked.st";
    private static final String DOCUMENT_CATALOG_SYSTEM = "classpath:/prompt/document/catalog-generation-system.st";
    private static final String DOCUMENT_CATALOG_USER = "classpath:/prompt/document/catalog-generation-user.st";
    private static final String DOCUMENT_SPECIFICITY_SYSTEM =
            "classpath:/prompt/document/specificity-classifier-system.st";
    private static final String DOCUMENT_SPECIFICITY_USER = "classpath:/prompt/document/specificity-classifier-user.st";
    private static final String SUBJECT_SYLLABUS_BUILDER_SYSTEM =
            "classpath:/prompt/subject/syllabus-builder-system.st";
    private static final String SUBJECT_SYLLABUS_BUILDER_USER = "classpath:/prompt/subject/syllabus-builder-user.st";

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public PromptResources(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public Resource baseIdentitySystemResource() {
        return resource(BASE_IDENTITY_SYSTEM);
    }

    public String guardClassifier() {
        return load(GUARD_CLASSIFIER);
    }

    public String compactionSystem() {
        return load(COMPACTION_SYSTEM);
    }

    public String compactionUser() {
        return load(COMPACTION_USER);
    }

    public String adaptiveTutorSystem() {
        return load(ADAPTIVE_TUTOR_SYSTEM);
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

    public String documentCatalogSystem() {
        return load(DOCUMENT_CATALOG_SYSTEM);
    }

    public String documentCatalogUser() {
        return load(DOCUMENT_CATALOG_USER);
    }

    public String documentSpecificitySystem() {
        return load(DOCUMENT_SPECIFICITY_SYSTEM);
    }

    public String documentSpecificityUser() {
        return load(DOCUMENT_SPECIFICITY_USER);
    }

    public String subjectSyllabusBuilderSystem() {
        return load(SUBJECT_SYLLABUS_BUILDER_SYSTEM);
    }

    public String subjectSyllabusBuilderUser() {
        return load(SUBJECT_SYLLABUS_BUILDER_USER);
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
