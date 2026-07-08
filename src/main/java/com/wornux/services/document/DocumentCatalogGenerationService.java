package com.wornux.services.document;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Service
public class DocumentCatalogGenerationService {

    private static final int MAX_USE_WHEN_LENGTH = 200;
    private static final int MAX_ALIASES = 8;
    private static final int MAX_ALIAS_LENGTH = 48;

    private final ChatModel chatModel;
    private final PromptResources promptResources;
    private final BeanOutputConverter<GeneratedCatalog> outputConverter =
            new BeanOutputConverter<>(GeneratedCatalog.class);
    private final BeanOutputConverter<SpecificityBatchClassification> specificityOutputConverter =
            new BeanOutputConverter<>(SpecificityBatchClassification.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                .messages(
                    new SystemMessage(promptResources.documentCatalogSystem()),
                    new UserMessage(userPrompt(title, useWhen, firstChunk)))
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

        var label = normalize(generated.label());
        var useWhen = normalize(generated.useWhen());
        if (label.isBlank()) {
            throw generatedTooGeneric("catalog label");
        }
        if (useWhen.isBlank()) {
            throw generatedTooGeneric("catalog usage rule");
        }

        var aliases = aliasCandidates(generated.aliases());
        var classifications = classifySpecificity(generatedSpecificityInputs(label, useWhen, aliases));

        if (!isSpecific(classifications, "generated_label")) {
            throw generatedTooGeneric("catalog label");
        }
        if (!isSpecific(classifications, "generated_use_when")) {
            throw generatedTooGeneric("catalog usage rule");
        }

        var specificAliases = aliases.stream()
                .filter(alias -> isSpecific(classifications, alias.id()))
                .map(SpecificityInput::value)
                .toList();
        if (specificAliases.size() < 3) {
            throw genericCatalogMessage();
        }

        return new CourseMaterialCatalog(label, useWhen, specificAliases);
    }

    private List<SpecificityInput> aliasCandidates(List<String> aliases) {
        if (aliases == null) {
            throw genericCatalogMessage();
        }
        var candidates = aliases.stream()
                .map(this::normalize)
                .filter(alias -> !alias.isBlank())
                .filter(alias -> alias.length() <= MAX_ALIAS_LENGTH)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), List::copyOf))
                .stream()
                .limit(MAX_ALIASES)
                .toList();
        return IntStream.range(0, candidates.size())
                .mapToObj(
                    index -> new SpecificityInput(
                        "alias_%d".formatted(index),
                        "catalog alias",
                        candidates.get(index)))
                .toList();
    }

    private List<SpecificityInput> generatedSpecificityInputs(
            String label,
            String useWhen,
            List<SpecificityInput> aliases) {
        var inputs = new ArrayList<SpecificityInput>();
        inputs.add(new SpecificityInput("generated_label", "catalog label", label));
        inputs.add(new SpecificityInput("generated_use_when", "catalog usage rule", useWhen));
        inputs.addAll(aliases);
        return inputs;
    }

    private void requireSpecificUseWhen(String useWhen) {
        var normalized = normalize(useWhen);
        if (normalized.isBlank()) {
            throw new DocumentIngestionException(
                    "Describe when the tutor should use this material before generating the catalog.");
        }
        if (normalized.length() > MAX_USE_WHEN_LENGTH) {
            throw new DocumentIngestionException("The usage description cannot exceed 200 characters.");
        }
        if (!isSpecific(normalized, "professor_usage_instruction", "professor usage instruction")) {
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
                .orElseThrow(
                    () -> new DocumentIngestionException(
                            "At least one non-empty chunk is required before generating the catalog."));
    }

    private boolean isSpecific(String value, String id, String fieldName) {
        return isSpecific(classifySpecificity(List.of(new SpecificityInput(id, fieldName, value))), id);
    }

    private boolean isSpecific(Map<String, SpecificityClassification> classifications, String id) {
        var classification = classifications.get(id);
        return classification != null && classification.specific();
    }

    private Map<String, SpecificityClassification> classifySpecificity(List<SpecificityInput> items) {
        if (items.isEmpty()) {
            return Map.of();
        }

        var prompt = Prompt.builder()
                .messages(
                    new SystemMessage(promptResources.documentSpecificitySystem()),
                    new UserMessage(specificityUserPrompt(items)))
                .chatOptions(chatOptions(specificityOutputConverter))
                .build();

        var classification = specificityOutputConverter.convert(responseText(chatModel.call(prompt)));
        if (classification == null || classification.items() == null) {
            throw genericCatalogMessage();
        }
        return classification.items().stream()
                .filter(item -> item.id() != null && !item.id().isBlank())
                .collect(Collectors.toMap(SpecificityClassification::id, Function.identity(), (left, _) -> left));
    }

    private String specificityUserPrompt(List<SpecificityInput> items) {
        try {
            return PromptUtil.render(
                promptResources.documentSpecificityUser(),
                Map.of("request", objectMapper.writeValueAsString(new SpecificityBatchRequest(items))));
        }
        catch (JsonProcessingException _) {
            throw new DocumentIngestionException("Failed to build the catalog specificity prompt.");
        }
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
            throw new DocumentIngestionException(
                    "The AI model did not return a usable response. Try generating again.");
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

    private DocumentIngestionException generatedTooGeneric(String fieldName) {
        return new DocumentIngestionException(
                "The generated %s is too generic. Improve the usage description and generate again."
                        .formatted(fieldName));
    }

    private record GeneratedCatalog(boolean createCatalog, String label, String useWhen, List<String> aliases) {}

    private record SpecificityBatchRequest(@JsonProperty(
            required = true) @JsonPropertyDescription("Texts to classify for course-material retrieval specificity.") @ArraySchema(
                    minItems = 1,
                    maxItems = 10,
                    schema = @Schema(implementation = SpecificityInput.class)) List<SpecificityInput> items) {}

    private record SpecificityInput(@JsonProperty(
            required = true) @JsonPropertyDescription("Stable identifier that must be echoed in the matching result.") @Schema(
                    example = "generated_label") String id,
            @JsonProperty(
                    required = true) @JsonPropertyDescription("Semantic field name for the text being classified.") @Schema(
                            example = "catalog label") String field,
            @JsonProperty(
                    required = true) @JsonPropertyDescription("Text value to classify independently from the other items.") String value) {}

    private record SpecificityBatchClassification(@JsonProperty(
            required = true) @JsonPropertyDescription("One specificity result for each input item, using the same item id.") @ArraySchema(
                    minItems = 1,
                    maxItems = 10,
                    schema = @Schema(implementation = SpecificityClassification.class)) List<SpecificityClassification> items) {}

    private record SpecificityClassification(@JsonProperty(
            required = true) @JsonPropertyDescription("Identifier copied from the classified input item.") @Schema(
                    example = "generated_label") String id,
            @JsonProperty(
                    required = true) @JsonPropertyDescription("True only when the text can distinguish this material from generic course content.") boolean specific,
            @JsonProperty(
                    required = true) @JsonPropertyDescription("Short reason for the specificity decision.") String reason) {}
}
