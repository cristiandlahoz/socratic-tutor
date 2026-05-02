package com.wornux.application.chat;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import java.util.Objects;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ConversationTitleService {

  private static final String TITLE_SYSTEM_PROMPT =
      """
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

  @Value("${app.ai.title.model}")
  private String titleModel;

  public ConversationTitleService(ChatModel chatModel) {
    this.chatModel = chatModel;
  }

  public Mono<String> generateTitle(String userPrompt) {
    return Mono.fromCallable(() -> generateTitleBlocking(userPrompt))
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(_ -> Mono.empty())
        .flatMap(
            title -> {
              if (title.isBlank()) {
                return Mono.empty();
              }
              return Mono.just(title);
            });
  }

  private String generateTitleBlocking(String userPrompt) {
    var prompt =
        Prompt.builder()
            .messages(new SystemMessage(TITLE_SYSTEM_PROMPT), new UserMessage(userPrompt))
            .chatOptions(
                OllamaChatOptions.builder()
                    .model(titleModel)
                    .temperature(0.0)
                    .format(outputConverter.getJsonSchemaMap())
                    .build())
            .build();

    var response = chatModel.call(prompt);
    var rawOutput =
        Objects.requireNonNull(Objects.requireNonNull(response.getResult()).getOutput().getText());
    var generatedConversationTitle = outputConverter.convert(rawOutput);
    return generatedConversationTitle.title();
  }

  private record GeneratedConversationTitle(String title) {}
}
