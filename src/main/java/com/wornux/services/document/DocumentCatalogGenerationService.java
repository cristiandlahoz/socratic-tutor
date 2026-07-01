package com.wornux.services.document;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.wornux.dtos.document.DocumentIngestionException;
import com.wornux.ui.ingestion.EditableSegmentViewModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentCatalogGenerationService {

    private static final int MAX_USE_WHEN_LENGTH = 200;
    private static final int MAX_ALIASES = 8;
    private static final int MAX_ALIAS_LENGTH = 48;
    private static final Set<String> GENERIC_TERMS = Set.of(
        "programming",
        "homework",
        "exercise",
        "class",
        "course",
        "document",
        "material",
        "pdf",
        "notes",
        "code",
        "study",
        "topic",
        "assignment",
        "programacion",
        "programación",
        "tarea",
        "ejercicio",
        "clase",
        "curso",
        "documento",
        "material",
        "codigo",
        "código",
        "estudio",
        "tema");
    private static final String SYSTEM_PROMPT = """
            You create tiny retrieval catalogs for course material.
            Return JSON only.

            Goal:
            - Help a tutor decide when stored course material is worth searching.
            - Be specific or fail. Generic catalogs are worse than no catalog.

            Rules:
            - Use the professor instruction as the source of truth.
            - Use the source label and first chunk only as supporting evidence.
            - Do not invent topics, assignments, rubrics, or document purpose.
            - Avoid generic terms at all cost.
            - Never use aliases such as: programming, homework, exercise, class, course, document, material, pdf, notes, code, study, topic, assignment.
            - If the professor instruction and first chunk are not specific enough, set createCatalog=false.
            - label: short specific name, max 60 chars.
            - useWhen: one specific sentence, max 200 chars.
            - aliases: 3 to 8 specific phrases, each max 48 chars.
            """;

    private final ChatModel chatModel;
    private final BeanOutputConverter<GeneratedCatalog> outputConverter = new BeanOutputConverter<>(GeneratedCatalog.class);

    @Value("${app.ai.switzerland-knife.model:${app.ai.guard.model}}")
    private String model;

    public DocumentCatalogGenerationService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public CourseMaterialCatalog generate(String title, String useWhen, List<EditableSegmentViewModel> segments) {
        requireSpecificUseWhen(useWhen);
        var generated = generatedCatalog(title, useWhen, firstChunk(segments));
        return validate(generated);
    }

    private GeneratedCatalog generatedCatalog(String title, String useWhen, String firstChunk) {
        var prompt = Prompt.builder()
                .messages(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userPrompt(title, useWhen, firstChunk)))
                .chatOptions(
                    OpenAiChatOptions.builder()
                            .model(model)
                            .temperature(0.0)
                            .outputSchema(outputConverter.getJsonSchema())
                            .build())
                .build();

        var response = chatModel.call(prompt);
        var content = Objects.requireNonNull(response.getResult().getOutput().getText());
        return outputConverter.convert(content);
    }

    private String userPrompt(String title, String useWhen, String firstChunk) {
        return """
               Source label:
               %s

               Professor use instruction:
               %s

               First chunk:
               %s
               """.formatted(title, useWhen, firstChunk);
    }

    private CourseMaterialCatalog validate(GeneratedCatalog generated) {
        if (generated == null || !generated.createCatalog()) {
            throw genericCatalogMessage();
        }

        var label = requireSpecificText(generated.label(), "catalog label");
        var useWhen = requireSpecificText(generated.useWhen(), "catalog usage rule");
        var aliases = aliases(generated.aliases());
        if (aliases.size() < 3) {
            throw genericCatalogMessage();
        }

        return new CourseMaterialCatalog(label, useWhen, aliases);
    }

    private List<String> aliases(List<String> aliases) {
        if (aliases == null) {
            throw genericCatalogMessage();
        }
        return aliases.stream()
                .map(this::normalize)
                .filter(alias -> !alias.isBlank())
                .filter(alias -> alias.length() <= MAX_ALIAS_LENGTH)
                .filter(this::isSpecific)
                .limit(MAX_ALIASES)
                .collect(java.util.stream.Collectors.collectingAndThen(
                    java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                    List::copyOf));
    }

    private String requireSpecificText(String value, String fieldName) {
        var normalized = normalize(value);
        if (normalized.isBlank() || !isSpecific(normalized)) {
            throw new DocumentIngestionException("The generated %s is too generic. Improve the usage description and generate again.".formatted(fieldName));
        }
        return normalized;
    }

    private void requireSpecificUseWhen(String useWhen) {
        var normalized = normalize(useWhen);
        if (normalized.isBlank()) {
            throw new DocumentIngestionException("Describe when the tutor should use this material before generating the catalog.");
        }
        if (normalized.length() > MAX_USE_WHEN_LENGTH) {
            throw new DocumentIngestionException("The usage description cannot exceed 200 characters.");
        }
        if (!isSpecific(normalized)) {
            throw genericCatalogMessage();
        }
    }

    private String firstChunk(List<EditableSegmentViewModel> segments) {
        if (segments == null) {
            throw new DocumentIngestionException("At least one chunk is required before generating the catalog.");
        }
        return segments.stream()
                .map(EditableSegmentViewModel::content)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElseThrow(() -> new DocumentIngestionException("At least one non-empty chunk is required before generating the catalog."));
    }

    private boolean isSpecific(String value) {
        var normalized = normalize(value).toLowerCase(Locale.ROOT);
        if (GENERIC_TERMS.contains(normalized)) {
            return false;
        }
        return GENERIC_TERMS.stream().noneMatch(term -> normalized.equals("use for " + term));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private DocumentIngestionException genericCatalogMessage() {
        return new DocumentIngestionException(
            "The catalog would be too generic. Add specific topics, assignment names, concepts, or situations and generate again.");
    }

    private record GeneratedCatalog(boolean createCatalog, String label, String useWhen, List<String> aliases) {}
}
