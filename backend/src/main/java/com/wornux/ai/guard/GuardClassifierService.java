package com.wornux.ai.guard;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.prompt.TutorPromptResources;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
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

/**
 * @author @github/cristiandlahoz
 */
@Service
public class GuardClassifierService {

  private final ChatModel chatModel;
  private final TutorPromptResources promptResources;
  private final BeanOutputConverter<GuardCheck> outputConverter =
      new BeanOutputConverter<>(GuardCheck.class);

  public GuardClassifierService(ChatModel chatModel, TutorPromptResources promptResources) {
    this.chatModel = chatModel;
    this.promptResources = promptResources;
  }

  @Value("${app.ai.guard.model}")
  private String guardModel;

  public GuardDecision classify(String userMessage) {
    Prompt prompt =
        Prompt.builder()
            .messages(
                new SystemMessage(promptResources.guardClassifier()), new UserMessage(userMessage))
            .chatOptions(
                OllamaChatOptions.builder()
                    .model(guardModel)
                    .temperature(0.0)
                    .format(outputConverter.getJsonSchemaMap())
                    .build())
            .build();

    GuardCheck guardCheck =
        outputConverter.convert(
            Objects.requireNonNull(
                Objects.requireNonNull(chatModel.call(prompt).getResult()).getOutput().getText()));
    if (guardCheck == null || guardCheck.decision() == null) {
      throw new IllegalStateException("Guard classifier returned an empty decision");
    }
    return guardCheck.decision();
  }
}
