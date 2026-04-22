package com.wornux.chat.routing;

import com.wornux.chat.TutorAiProperties;
import com.wornux.chat.prompt.TutorPromptResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class PedagogicalRoutingService {

    private static final Logger log = LoggerFactory.getLogger(PedagogicalRoutingService.class);

    private static final Pattern DEBUG_ATTEMPT_PATTERN = Pattern.compile(
            "(?s)(```|#include\\s*<|scanf\\s*\\(|printf\\s*\\(|mi\\s+codigo|llevo\\s+esto|este\\s+es\\s+mi\\s+codigo|"
                    + "aqui\\s+esta\\s+mi\\s+intento|aqui\\s+esta\\s+mi\\s+solucion|here\\s+is\\s+my\\s+attempt|"
                    + "here\\s+is\\s+my\\s+code|my\\s+attempt|ya\\s+lo\\s+resolvi\\s+casi|casi\\s+lo\\s+resuelvo|"
                    + "tengo\\s+este\\s+error|este\\s+fragmento|this\\s+snippet)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EXERCISE_GUIDANCE_PATTERN = Pattern.compile(
            "\\b(resuelv(e|eme|elo)|dame\\s+la\\s+respuesta|respuesta\\s+final|haz\\s+mi\\s+tarea|solo\\s+codigo|"
                    + "resuelve\\s+este\\s+ejercicio|ejercicio|tarea|quiz|problema|solve\\s+(it|this)|"
                    + "final\\s+answer|just\\s+the\\s+answer|do\\s+my\\s+homework|only\\s+code)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DIRECT_REFERENCE_PATTERN = Pattern.compile(
            "\\b(sintaxis|syntax|formato|format|como\\s+hacer|como\\s+se\\s+escribe|how\\s+do\\s+i\\s+write|"
                    + "ejemplo\\s+de|example\\s+of|que\\s+recibe|what\\s+does\\s+.+\\s+receive|uso\\s+de|how\\s+to\\s+use|"
                    + "firma\\s+de|signature\\s+of|for\\s+loop|scanf|printf|strlen)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CONCEPT_EXPLANATION_PATTERN = Pattern.compile(
            "\\b(que\\s+es|que\\s+hace|para\\s+que\\s+sirve|explica|explicame|explicame|difference\\s+between|"
                    + "diferencia\\s+entre|how\\s+does|why\\s+does|what\\s+is|explain)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final ChatModel chatModel;
    private final TutorPromptResources promptResources;
    private final TutorAiProperties tutorAiProperties;
    private final BeanOutputConverter<RoutingDecision> outputConverter = new BeanOutputConverter<>(RoutingDecision.class);

    public PedagogicalRoutingService(ChatModel chatModel, TutorPromptResources promptResources, TutorAiProperties tutorAiProperties) {
        this.chatModel = chatModel;
        this.promptResources = promptResources;
        this.tutorAiProperties = tutorAiProperties;
    }

    public PedagogicalRoutingMode classify(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim();
        if (normalized.isBlank()) {
            return PedagogicalRoutingMode.CONCEPT_EXPLANATION;
        }

        return heuristicDecisionFor(normalized)
                .orElseGet(() -> classifyWithModel(normalized));
    }

    Optional<PedagogicalRoutingMode> heuristicDecisionFor(String userMessage) {
        if (DEBUG_ATTEMPT_PATTERN.matcher(userMessage).find()) {
            return Optional.of(PedagogicalRoutingMode.DEBUG_MY_ATTEMPT);
        }
        if (EXERCISE_GUIDANCE_PATTERN.matcher(userMessage).find()) {
            return Optional.of(PedagogicalRoutingMode.EXERCISE_GUIDANCE);
        }
        if (DIRECT_REFERENCE_PATTERN.matcher(userMessage).find()) {
            return Optional.of(PedagogicalRoutingMode.DIRECT_REFERENCE);
        }
        if (CONCEPT_EXPLANATION_PATTERN.matcher(userMessage).find()) {
            return Optional.of(PedagogicalRoutingMode.CONCEPT_EXPLANATION);
        }
        return Optional.empty();
    }

    private PedagogicalRoutingMode classifyWithModel(String userMessage) {
        try {
            Prompt prompt = Prompt.builder()
                    .messages(
                            new SystemMessage(promptResources.routingClassifier()),
                            new UserMessage(userMessage)
                    )
                    .chatOptions(OllamaChatOptions.builder()
                            .model(tutorAiProperties.getRoutingModel())
                            .temperature(0.0)
                            .format(outputConverter.getJsonSchemaMap())
                            .build())
                    .build();

            var response = chatModel.call(prompt);
            var content = Objects.requireNonNull(Objects.requireNonNull(response.getResult()).getOutput().getText());
            RoutingDecision decision = outputConverter.convert(content);
            if (decision == null || decision.mode() == null) {
                throw new IllegalStateException("Routing classifier returned an empty mode");
            }
            return decision.mode();
        } catch (RuntimeException exception) {
            log.warn("Routing classifier failed, defaulting to concept explanation", exception);
            return PedagogicalRoutingMode.CONCEPT_EXPLANATION;
        }
    }

    record RoutingDecision(PedagogicalRoutingMode mode) {
    }
}
