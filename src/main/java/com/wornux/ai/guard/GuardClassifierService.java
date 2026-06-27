package com.wornux.ai.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.wornux.ai.prompt.TutorPromptResources;
import com.wornux.data.enums.GuardDecision;
import com.wornux.dtos.chat.GuardCheck;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * @author @github/cristiandlahoz
 */
@Service
public class GuardClassifierService {

    private final ChatModel chatModel;
    private final TutorPromptResources promptResources;
    private final BeanOutputConverter<GuardCheck> outputConverter = new BeanOutputConverter<>(GuardCheck.class);

    @Value("${app.ai.guard.model}")
    private String guardModel;

    public GuardClassifierService(ChatModel chatModel, TutorPromptResources promptResources) {
        this.chatModel = chatModel;
        this.promptResources = promptResources;
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

        String responseText = Objects.requireNonNull(
            Objects.requireNonNull(chatModel.call(prompt).getResult()).getOutput().getText(),
            "Guard classifier returned an empty response");

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
