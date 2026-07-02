package com.wornux.services.document;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.wornux.ai.prompt.PromptResources;
import com.wornux.ai.prompt.PromptUtil;
import com.wornux.dtos.document.DocumentIngestionException;
import com.wornux.ui.ingestion.EditableSegmentViewModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
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

    private final ChatModel chatModel;
    private final PromptResources promptResources;
    private final BeanOutputConverter<GeneratedCatalog> outputConverter = new BeanOutputConverter<>(GeneratedCatalog.class);
    private final BeanOutputConverter<SpecificityClassification> specificityOutputConverter = new BeanOutputConverter<>(SpecificityClassification.class);

    @Value("${app.ai.switzerland-knife.model:${app.ai.guard.model}}")
    private String model;

    public DocumentCatalogGenerationService(ChatModel chatModel, PromptResources promptResources) {
        this.chatModel = chatModel;
        this.promptResources = promptResources;
    }

    public CourseMaterialCatalog generate(String title, String useWhen, List<EditableSegmentViewModel> segments) {
        requireSpecificUseWhen(useWhen);
        var generated = generatedCatalog(title, useWhen, firstChunk(segments));
        return validate(generated);
    }

    private GeneratedCatalog generatedCatalog(String title, String useWhen, String firstChunk) {
        var prompt = Prompt.builder()
                .messages(new SystemMessage(promptResources.documentCatalogSystem()), new UserMessage(userPrompt(title, useWhen, firstChunk)))
                .chatOptions(chatOptions(outputConverter))
                .build();

        return outputConverter.convert(responseText(chatModel.call(prompt)));
    }

    private String userPrompt(String title, String useWhen, String firstChunk) {
        return PromptUtil.render(
            promptResources.documentCatalogUser(),
            Map.of("title", normalize(title), "useWhen", normalize(useWhen), "firstChunk", firstChunk));
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
                .filter(alias -> isSpecific(alias, "catalog alias"))
                .limit(MAX_ALIASES)
                .collect(java.util.stream.Collectors.collectingAndThen(
                    java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                    List::copyOf));
    }

    private String requireSpecificText(String value, String fieldName) {
        var normalized = normalize(value);
        if (normalized.isBlank() || !isSpecific(normalized, fieldName)) {
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
        if (!isSpecific(normalized, "professor usage instruction")) {
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

    private boolean isSpecific(String value, String fieldName) {
        var prompt = Prompt.builder()
                .messages(
                    new SystemMessage(promptResources.documentSpecificitySystem()),
                    new UserMessage(specificityUserPrompt(fieldName, value)))
                .chatOptions(chatOptions(specificityOutputConverter))
                .build();

        var classification = specificityOutputConverter.convert(responseText(chatModel.call(prompt)));
        return classification != null && classification.specific();
    }

    private String specificityUserPrompt(String fieldName, String value) {
        return PromptUtil.render(
            promptResources.documentSpecificityUser(),
            Map.of("fieldName", fieldName, "value", value));
    }

    private OpenAiChatOptions chatOptions(BeanOutputConverter<?> converter) {
        return OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.0)
                .outputSchema(converter.getJsonSchema())
                .build();
    }

    private String responseText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new DocumentIngestionException("The AI model did not return a usable response. Try generating again.");
        }

        var text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new DocumentIngestionException("The AI model returned an empty response. Try generating again.");
        }
        return text;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private DocumentIngestionException genericCatalogMessage() {
        return new DocumentIngestionException(
            "The catalog would be too generic. Add specific topics, assignment names, concepts, or situations and generate again.");
    }

    private record GeneratedCatalog(boolean createCatalog, String label, String useWhen, List<String> aliases) {}

    private record SpecificityClassification(boolean specific, String reason) {}
}
