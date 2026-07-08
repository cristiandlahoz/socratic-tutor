package com.wornux.services.workspace;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.wornux.ai.prompt.PromptResources;
import com.wornux.ai.prompt.PromptUtil;
import com.wornux.dtos.document.DocumentIngestionException;
import com.wornux.infrastructure.external.docling.DoclingClientService;
import com.wornux.config.ApplicationProperties;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class SubjectSyllabusGenerationService {

    private static final int MAX_MARKDOWN_CHARS = 16_000;
    private static final int MAX_SYLLABUS_CHARS = 1_200;

    private final DoclingClientService doclingClientService;
    private final ChatModel chatModel;
    private final PromptResources promptResources;
    private final BeanOutputConverter<GeneratedSyllabusContext> outputConverter =
            new BeanOutputConverter<>(GeneratedSyllabusContext.class);
    private final String model;

    public SubjectSyllabusGenerationService(
            DoclingClientService doclingClientService,
            ChatModel chatModel,
            PromptResources promptResources,
            ApplicationProperties.Ai.SwitzerlandKnife switzerlandKnifeProperties) {
        this.doclingClientService = doclingClientService;
        this.chatModel = chatModel;
        this.promptResources = promptResources;
        this.model = switzerlandKnifeProperties.getModel();
    }

    public String generateFromPdf(String code, String name, String filename, byte[] content) {
        if (filename == null || filename.isBlank() || !filename.toLowerCase().endsWith(".pdf")) {
            throw new DocumentIngestionException("Sube un PDF de syllabus válido.");
        }
        if (content == null || content.length == 0) {
            throw new DocumentIngestionException("El PDF de syllabus está vacío.");
        }

        var markdown = doclingClientService.convertPdfToMarkdown(filename, content);
        var generated = outputConverter.convert(responseText(chatModel.call(prompt(code, name, markdown))));
        if (generated == null || !generated.acceptable() || generated.syllabusContext() == null
                || generated.syllabusContext().isBlank()) {
            throw new DocumentIngestionException(
                    "El PDF no produjo un contexto de asignatura suficientemente específico. Escribe competencias y stack manualmente.");
        }
        var context = generated.syllabusContext().trim();
        return context.length() <= MAX_SYLLABUS_CHARS ? context : context.substring(0, MAX_SYLLABUS_CHARS).trim();
    }

    private Prompt prompt(String code, String name, String markdown) {
        return Prompt.builder()
                .messages(
                    new SystemMessage(promptResources.subjectSyllabusBuilderSystem()),
                    new UserMessage(PromptUtil.render(
                        promptResources.subjectSyllabusBuilderUser(),
                        Map.of(
                            "code",
                            normalize(code),
                            "name",
                            normalize(name),
                            "markdown",
                            truncate(markdown, MAX_MARKDOWN_CHARS)))))
                .chatOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.0)
                        .outputSchema(outputConverter.getJsonSchema())
                        .build())
                .build();
    }

    private String responseText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new DocumentIngestionException("El modelo no devolvió un contexto de asignatura utilizable.");
        }
        var text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new DocumentIngestionException("El modelo devolvió un contexto de asignatura vacío.");
        }
        return text;
    }

    private String truncate(String value, int maxChars) {
        var normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record GeneratedSyllabusContext(
            @JsonProperty(required = true) @JsonPropertyDescription(
                    "True only when the source document is specific enough to constrain tutor behavior.") boolean acceptable,
            @JsonProperty(required = true) @JsonPropertyDescription(
                    "Short quality note explaining why the document passed or failed.") String qualityNotes,
            @JsonProperty(required = true) @JsonPropertyDescription(
                    "Compact action-oriented subject context for system-prompt injection, max 1200 characters.") String syllabusContext) {}
}
