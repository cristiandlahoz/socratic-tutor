package com.wornux.ai.routing;

import java.util.Objects;

import com.wornux.ai.prompt.TutorPromptResources;
import com.wornux.config.TutorAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class PedagogicalRoutingService {

    private static final Logger log = LoggerFactory.getLogger(PedagogicalRoutingService.class);

    private final ChatModel chatModel;
    private final TutorPromptResources promptResources;
    private final TutorAiProperties tutorAiProperties;
    private final BeanOutputConverter<RoutingDecision> outputConverter =
            new BeanOutputConverter<>(RoutingDecision.class);

    public PedagogicalRoutingService(
            ChatModel chatModel,
            TutorPromptResources promptResources,
            TutorAiProperties tutorAiProperties) {
        this.chatModel = chatModel;
        this.promptResources = promptResources;
        this.tutorAiProperties = tutorAiProperties;
    }

    public PedagogicalRoutingMode classify(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim();
        if (normalized.isBlank()) {
            return PedagogicalRoutingMode.CONCEPT_EXPLANATION;
        }

        return classifyWithModel(normalized);
    }

    private PedagogicalRoutingMode classifyWithModel(String userMessage) {
        try {
            Prompt prompt = Prompt.builder()
                    .messages(new SystemMessage(promptResources.routingClassifier()), new UserMessage(userMessage))
                    .chatOptions(
                        OpenAiChatOptions.builder()
                                .model(tutorAiProperties.getRoutingModel())
                                .temperature(0.0)
                                .outputSchema(outputConverter.getJsonSchema())
                                .build())
                    .build();

            var response = chatModel.call(prompt);
            var content = Objects.requireNonNull(Objects.requireNonNull(response.getResult()).getOutput().getText());
            RoutingDecision decision = outputConverter.convert(content);
            if (decision == null || decision.mode() == null) {
                throw new IllegalStateException("Routing classifier returned an empty mode");
            }
            return decision.mode();
        }
        catch (RuntimeException exception) {
            log.warn("Routing classifier failed, defaulting to concept explanation", exception);
            return PedagogicalRoutingMode.CONCEPT_EXPLANATION;
        }
    }

    record RoutingDecision(PedagogicalRoutingMode mode) {}
}
