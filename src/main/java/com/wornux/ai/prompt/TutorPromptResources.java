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
public class TutorPromptResources {

    private static final String BASE_IDENTITY_SYSTEM = "classpath:/tutor/base-identity-system.st";
    private static final String DIRECT_REFERENCE_EXAMPLES = "classpath:/tutor/examples/direct-reference.st";
    private static final String EXERCISE_GUIDANCE_EXAMPLES = "classpath:/tutor/examples/exercise-guidance.st";
    private static final String GUARD_NOT_SAFE = "classpath:/tutor/policies/guard-not-safe.st";
    private static final String GUARD_IMPERSONATION = "classpath:/tutor/policies/guard-impersonation.st";
    private static final String GUARD_OUT_OF_SCOPE = "classpath:/tutor/policies/guard-out-of-scope.st";
    private static final String GUARD_CLASSIFIER = "classpath:/tutor/policies/guard-classifier.st";
    private static final String ROUTING_CLASSIFIER = "classpath:/tutor/policies/routing-classifier.st";
    private static final String ROUTING_DIRECT_REFERENCE = "classpath:/tutor/policies/routing-direct-reference.st";
    private static final String ROUTING_EXERCISE_GUIDANCE = "classpath:/tutor/policies/routing-exercise-guidance.st";
    private static final String ROUTING_DEBUG_MY_ATTEMPT = "classpath:/tutor/policies/routing-debug-my-attempt.st";
    private static final String ROUTING_CONCEPT_EXPLANATION =
            "classpath:/tutor/policies/routing-concept-explanation.st";

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public TutorPromptResources(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public Resource baseIdentitySystemResource() {
        return resource(BASE_IDENTITY_SYSTEM);
    }

    public String directReferenceExamples() {
        return load(DIRECT_REFERENCE_EXAMPLES);
    }

    public String exerciseGuidanceExamples() {
        return load(EXERCISE_GUIDANCE_EXAMPLES);
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

    public String routingClassifier() {
        return load(ROUTING_CLASSIFIER);
    }

    public String routingDirectReference() {
        return load(ROUTING_DIRECT_REFERENCE);
    }

    public String routingExerciseGuidance() {
        return load(ROUTING_EXERCISE_GUIDANCE);
    }

    public String routingDebugMyAttempt() {
        return load(ROUTING_DEBUG_MY_ATTEMPT);
    }

    public String routingConceptExplanation() {
        return load(ROUTING_CONCEPT_EXPLANATION);
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
            throw new UncheckedIOException("Failed to read prompt resource " + location, exception);
        }
    }

    private Resource resource(String location) {
        return resourceLoader.getResource(location);
    }
}
