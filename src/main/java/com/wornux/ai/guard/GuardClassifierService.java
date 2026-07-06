package com.wornux.ai.guard;

import java.util.ArrayList;
import java.util.List;

import com.wornux.ai.prompt.PromptResources;
import com.wornux.data.enums.GuardDecision;
import com.wornux.dtos.chat.GuardCheck;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * @author @github/cristiandlahoz
 */
@Service
public class GuardClassifierService {

    private final ChatModel chatModel;
    private final PromptResources promptResources;
    private final BeanOutputConverter<GuardCheck> outputConverter = new BeanOutputConverter<>(GuardCheck.class);

    private final String guardModel;

    public GuardClassifierService(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            PromptResources promptResources,
            @Value("${app.ai.guard.model}") String guardModel) {
        this.chatModel = chatModel;
        this.promptResources = promptResources;
        this.guardModel = guardModel;
    }

    public GuardDecision classify(List<UserMessage> userMessages) {
        Assert.notEmpty(userMessages, "userMessages cannot be empty");

        Prompt prompt = Prompt.builder()
                .messages(classifierMessages(userMessages))
                .chatOptions(
                    OpenAiChatOptions.builder()
                            .model(guardModel)
                            .temperature(0.0)
                            .outputSchema(outputConverter.getJsonSchema())
                            .build())
                .build();

        var generation = chatModel.call(prompt).getResult();
        if (generation == null) {
            throw new IllegalStateException("Guard classifier returned no generation");
        }
        String responseText = generation.getOutput().getText();
        if (responseText == null || responseText.isBlank()) {
            throw new IllegalStateException("Guard classifier returned an empty response");
        }

        GuardCheck guardCheck = outputConverter.convert(responseText);

        if (guardCheck == null || guardCheck.decision() == null) {
            throw new IllegalStateException("Guard classifier returned an empty decision");
        }

        return guardCheck.decision();
    }

    private List<Message> classifierMessages(List<UserMessage> userMessages) {
        List<Message> messages = new ArrayList<>(userMessages.size() + 1);
        messages.add(new SystemMessage(promptResources.guardClassifier()));
        messages.addAll(userMessages);
        return messages;
    }
}
