package com.wornux.services.chat;

import java.util.Objects;

import com.wornux.config.ApplicationProperties;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ConversationTitleService {

    private static final String TITLE_SYSTEM_PROMPT = """
                                                      You generate short titles for student chat sessions.
                                                      Return JSON only.

                                                      Rules:
                                                      - Keep the title in the same language as the user input.
                                                      - Summarize only the main intent.
                                                      - Maximum 72 characters.
                                                      - No quotes.
                                                      - No markdown.
                                                      - No trailing punctuation.
                                                      """;

    private final ChatModel chatModel;
    private final BeanOutputConverter<GeneratedConversationTitle> outputConverter =
            new BeanOutputConverter<>(GeneratedConversationTitle.class);
    private final String titleModel;

    public ConversationTitleService(
            ChatModel chatModel,
            ApplicationProperties.Ai.SwitzerlandKnife switzerlandKnifeProperties) {
        this.chatModel = chatModel;
        this.titleModel = switzerlandKnifeProperties.getModel();
    }

    public Mono<String> generateTitle(String userPrompt) {
        return Mono.fromCallable(() -> generateTitleBlocking(userPrompt))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(_ -> Mono.empty())
                .flatMap(title -> {
                    if (title.isBlank()) {
                        return Mono.empty();
                    }
                    return Mono.just(title);
                });
    }

    private String generateTitleBlocking(String userPrompt) {
        var prompt = Prompt.builder()
                .messages(new SystemMessage(TITLE_SYSTEM_PROMPT), new UserMessage(userPrompt))
                .chatOptions(
                    OpenAiChatOptions.builder()
                            .model(titleModel)
                            .temperature(0.0)
                            .outputSchema(outputConverter.getJsonSchema())
                            .build())
                .build();

        var response = chatModel.call(prompt);
        var rawOutput = Objects.requireNonNull(Objects.requireNonNull(response.getResult()).getOutput().getText());
        var generatedConversationTitle = outputConverter.convert(rawOutput);
        return generatedConversationTitle.title();
    }

    private record GeneratedConversationTitle(String title) {}
}
