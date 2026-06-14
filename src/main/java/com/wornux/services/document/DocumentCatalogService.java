package com.wornux.services.document;

import java.util.Objects;

import com.wornux.config.DocumentIngestionProperties;
import com.wornux.dtos.document.*;
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
public class DocumentCatalogService {

    private static final Logger log = LoggerFactory.getLogger(DocumentCatalogService.class);
    private static final int MAX_MARKDOWN_CHARS = 12_000;
    private static final String SYSTEM_PROMPT =
            """
            You classify converted PDF markdown into a compact searchable catalog entry.
            Return JSON only.

            Rules:
            - Keep everything concise and useful for deciding whether to search this document later.
            - title: short human title, max 12 words.
            - topic: one sentence describing what the document is about.
            - summary: max 45 words.
            - tags: 3 to 8 lowercase searchable tags.
            - entities: important names, concepts, organizations, places, laws, dates, or course concepts.
            - questionExamples: realistic questions this document can answer.
            - Do not invent facts not supported by the document.
            """;

    private final ChatModel chatModel;
    private final DocumentIngestionProperties properties;
    private final BeanOutputConverter<DocumentCatalogEntry> outputConverter =
            new BeanOutputConverter<>(DocumentCatalogEntry.class);

    @Value("${app.ai.document-catalog.model:${spring.ai.ollama.chat.model}}")
    private String documentCatalogModel;

    public DocumentCatalogService(ChatModel chatModel, DocumentIngestionProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    public CatalogAnalysis analyzeOrFallback(String filename, String markdown) {
        try {
            var prompt = Prompt.builder()
                    .messages(
                        new SystemMessage(SYSTEM_PROMPT),
                        new UserMessage("""
                                        Filename: %s

                                        Markdown:
                                        %s
                                        """.formatted(filename, trimMarkdown(markdown))))
                    .chatOptions(
                        OllamaChatOptions.builder()
                                .model(documentCatalogModel)
                                .temperature(0.0)
                                .format(outputConverter.getJsonSchemaMap())
                                .build())
                    .build();

            var response = chatModel.call(prompt);
            var rawOutput = Objects.requireNonNull(Objects.requireNonNull(response.getResult()).getOutput().getText());
            var catalog = outputConverter.convert(rawOutput);
            var normalized = catalog
                    .normalized(properties.getCatalog().getMaxTags(), properties.getCatalog().getMaxQuestionExamples());
            log.info(
                "document_catalog_generated filename={} title={} tags={} questions={}",
                filename,
                normalized.title(),
                normalized.tags().size(),
                normalized.questionExamples().size());
            return new CatalogAnalysis(normalized, false);
        }
        catch (RuntimeException exception) {
            var fallback = DocumentCatalogEntry.fallback(filename, markdown)
                    .normalized(properties.getCatalog().getMaxTags(), properties.getCatalog().getMaxQuestionExamples());
            log.warn("document_catalog_generation_failed filename={}", filename, exception);
            return new CatalogAnalysis(fallback, true);
        }
    }

    private String trimMarkdown(String markdown) {
        if (markdown == null || markdown.length() <= MAX_MARKDOWN_CHARS) {
            return markdown == null ? "" : markdown;
        }
        return markdown.substring(0, MAX_MARKDOWN_CHARS);
    }

    public record CatalogAnalysis(DocumentCatalogEntry entry, boolean stale) {}
}
