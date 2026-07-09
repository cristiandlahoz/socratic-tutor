package com.wornux.ai.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.wornux.ai.prompt.PromptResources;
import com.wornux.ai.prompt.PromptUtil;
import com.wornux.config.ApplicationProperties;
import com.wornux.data.enums.GuardAction;
import com.wornux.data.enums.GuardDecision;
import com.wornux.dtos.chat.GuardCheck;
import com.wornux.dtos.chat.GuardSanitization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * @author @github/cristiandlahoz
 */
@Service
@Slf4j
public class GuardClassifierService {

    private final ChatModel chatModel;
    private final PromptResources promptResources;
    private final BeanOutputConverter<GuardCheck> outputConverter = new BeanOutputConverter<>(GuardCheck.class);
    private final BeanOutputConverter<GuardSanitization> sanitizerOutputConverter =
            new BeanOutputConverter<>(GuardSanitization.class);

    private final String guardModel;

    public GuardClassifierService(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            PromptResources promptResources,
            ApplicationProperties.Ai.SwitzerlandKnife switzerlandKnifeProperties) {
        this.chatModel = chatModel;
        this.promptResources = promptResources;
        this.guardModel = switzerlandKnifeProperties.getModel();
    }

    public GuardCheck classify(List<UserMessage> userMessages) {
        return classify(userMessages, "");
    }

    public GuardCheck classify(List<UserMessage> userMessages, String subjectContext) {
        Assert.notEmpty(userMessages, "userMessages cannot be empty");

        Prompt prompt = Prompt.builder()
                .messages(classifierMessages(userMessages, subjectContext))
                .chatOptions(options(outputConverter))
                .build();

        GuardCheck guardCheck = outputConverter.convert(callGuardModel(prompt, "classifier"));

        if (guardCheck == null || guardCheck.decision() == null) {
            throw new IllegalStateException("Guard classifier returned an empty decision");
        }
        guardCheck = normalize(guardCheck);

        log.info("Guard decision: {}, action: {}", guardCheck.decision(), guardCheck.action());
        return guardCheck;
    }

    private GuardCheck normalize(GuardCheck guardCheck) {
        var action = guardCheck.action() == null ? GuardAction.SHORT_CIRCUIT : guardCheck.action();
        if (guardCheck.decision() == GuardDecision.SAFE) {
            action = GuardAction.ALLOW;
        }
        else if (action == GuardAction.ALLOW) {
            action = GuardAction.SHORT_CIRCUIT;
        }
        return new GuardCheck(guardCheck.decision(), action);
    }

    public String sanitize(UserMessage userMessage, String subjectContext) {
        Prompt prompt = Prompt.builder()
                .messages(List.of(new SystemMessage(render(promptResources.guardSanitizer(), subjectContext)), userMessage))
                .chatOptions(options(sanitizerOutputConverter))
                .build();

        GuardSanitization sanitization = sanitizerOutputConverter.convert(callGuardModel(prompt, "sanitizer"));
        if (sanitization == null || sanitization.sanitizedMessage() == null
                || sanitization.sanitizedMessage().isBlank()) {
            throw new IllegalStateException("Guard sanitizer returned an empty sanitized message");
        }
        return sanitization.sanitizedMessage().trim();
    }

    private OpenAiChatOptions options(BeanOutputConverter<?> converter) {
        return OpenAiChatOptions.builder()
                .model(guardModel)
                .temperature(0.0)
                .outputSchema(converter.getJsonSchema())
                .build();
    }

    private String callGuardModel(Prompt prompt, String operation) {
        var generation = chatModel.call(prompt).getResult();
        if (generation == null) {
            throw new IllegalStateException("Guard %s returned no generation".formatted(operation));
        }
        String responseText = generation.getOutput().getText();
        if (responseText == null || responseText.isBlank()) {
            throw new IllegalStateException("Guard %s returned an empty response".formatted(operation));
        }
        return responseText;
    }

    private List<Message> classifierMessages(List<UserMessage> userMessages, String subjectContext) {
        List<Message> messages = new ArrayList<>(userMessages.size() + 1);
        messages.add(new SystemMessage(render(promptResources.guardClassifier(), subjectContext)));
        messages.addAll(userMessages);
        return messages;
    }

    private String render(String template, String subjectContext) {
        return PromptUtil.render(template, Map.of("subjectContext", subjectContext == null ? "" : subjectContext));
    }
}
