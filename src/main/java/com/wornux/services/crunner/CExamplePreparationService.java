package com.wornux.services.crunner;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CExamplePreparationService {

  private static final Logger log = LoggerFactory.getLogger(CExamplePreparationService.class);

  private static final String SYSTEM_PROMPT =
      """
      You convert illustrative C teaching snippets into minimally runnable C17 programs.
      Preserve the educational intent and keep the original snippet as intact as possible.
      You may add scaffolding such as #include directives, int main(void), minimal variable declarations,
      minimal sample values, printf statements to observe demonstrated values, and return 0.
      Do not solve TODOs, replace algorithms, optimize, refactor, or introduce advanced concepts absent from the snippet.
      If making the snippet runnable requires solving an exercise, return NOT_RUNNABLE.
      Return JSON only using the requested schema.
      """;

  private final ChatModel chatModel;
  private final String model;
  private final BeanOutputConverter<CExamplePreparationResult> outputConverter =
      new BeanOutputConverter<>(CExamplePreparationResult.class);

  public CExamplePreparationService(
      ChatModel chatModel,
      @Value("${app.ai.c-example-preparation.model:${spring.ai.ollama.chat.model}}") String model) {
    this.chatModel = chatModel;
    this.model = model;
  }

  public CExamplePreparationResult prepare(String source, String language) {
    var normalizedSource = source == null ? "" : source.trim();
    if (normalizedSource.isBlank() || !isSupportedLanguage(language)) {
      return notRunnable(normalizedSource, "unsupported-or-empty");
    }
    try {
      return normalize(callModel(normalizedSource, language), normalizedSource);
    } catch (RuntimeException ex) {
      log.warn("c_example_preparation_failed language={}", language, ex);
      return CExamplePreparationResult.readyOriginal(normalizedSource);
    }
  }

  private CExamplePreparationResult callModel(String source, String language) {
    var prompt =
        Prompt.builder()
            .messages(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userPrompt(source, language)))
            .chatOptions(
                OllamaChatOptions.builder()
                    .model(model)
                    .temperature(0.0)
                    .disableThinking()
                    .format(outputConverter.getJsonSchemaMap())
                    .build())
            .build();
    var response = chatModel.call(prompt);
    var content =
        Objects.requireNonNull(Objects.requireNonNull(response.getResult()).getOutput().getText());
    return outputConverter.convert(content);
  }

  private CExamplePreparationResult normalize(
      CExamplePreparationResult result, String fallbackSource) {
    if (result == null) {
      return CExamplePreparationResult.readyOriginal(fallbackSource);
    }
    if (result.status() == CExamplePreparationStatus.READY && result.source().isBlank()) {
      return CExamplePreparationResult.readyOriginal(fallbackSource);
    }
    return result;
  }

  private static String userPrompt(String source, String language) {
    return """
    Language: %s

    Snippet:
    ```%s
    %s
    ```

    Return fields:
    - status: READY or NOT_RUNNABLE
    - source: complete runnable C17 source when READY, otherwise empty
    - changes: short list of scaffolding changes
    - educationalNote: one short sentence for the student
    - risk: none, too_incomplete, ambiguous, unsupported, or preparation-unavailable
    """
        .formatted(language, language, source);
  }

  private static boolean isSupportedLanguage(String language) {
    var normalized = language == null ? "" : language.trim().toLowerCase();
    return normalized.equals("c") || normalized.equals("c17");
  }

  private static CExamplePreparationResult notRunnable(String source, String risk) {
    return new CExamplePreparationResult(
        CExamplePreparationStatus.NOT_RUNNABLE,
        "",
        List.of(),
        source.isBlank() ? "Paste C code to debug." : "This example cannot be prepared automatically.",
        risk);
  }
}
