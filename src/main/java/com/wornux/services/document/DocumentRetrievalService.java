package com.wornux.services.document;

import com.wornux.config.DocumentIngestionProperties;
import com.wornux.domain.document.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.stereotype.Service;

@Service
public class DocumentRetrievalService {

    private final VectorStore vectorStore;
    private final DocumentIngestionProperties properties;
    private final FilterExpressionTextParser filterExpressionTextParser = new FilterExpressionTextParser();

    public DocumentRetrievalService(VectorStore vectorStore, DocumentIngestionProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public DocumentContextResult search(
            UUID clientId,
            String query,
            String documentIdHint,
            String filenameHint,
            String topicHint,
            String tagHint) {
        String filterExpression = "clientId == '%s'".formatted(clientId);
        if (documentIdHint != null && !documentIdHint.isBlank()) {
            filterExpression += " && documentId == '%s'".formatted(escape(documentIdHint));
        }
        if (filenameHint != null && !filenameHint.isBlank()) {
            filterExpression += " && filename == '%s'".formatted(escape(filenameHint));
        }

        var composedQuery = composeQuery(query, topicHint, tagHint);
        var request = SearchRequest.builder()
                .query(composedQuery)
                .topK(properties.getRetrievalTopK())
                .similarityThreshold(properties.getRetrievalSimilarityThreshold())
                .filterExpression(filterExpressionTextParser.parse(filterExpression))
                .build();

        List<DocumentSearchHit> hits = vectorStore.similaritySearch(request)
                .stream()
                .map(
                    document -> toSearchHit(
                        document.getId(),
                        document.getText(),
                        document.getMetadata(),
                        document.getScore()))
                .toList();
        return new DocumentContextResult(hits, !hits.isEmpty());
    }

    private DocumentSearchHit toSearchHit(
            String segmentId,
            String content,
            Map<String, Object> metadata,
            Double score) {
        return new DocumentSearchHit(segmentId,
                UUID.fromString(String.valueOf(metadata.get("documentId"))),
                String.valueOf(metadata.getOrDefault("filename", "Documento PDF")),
                String.valueOf(metadata.getOrDefault("documentTitle", "")),
                String.valueOf(metadata.getOrDefault("documentTopic", "")),
                parseTags(metadata.get("documentTags")),
                String.valueOf(metadata.getOrDefault("headingPath", "Documento")),
                summarize(content),
                score,
                parseInteger(metadata.get("segmentOrdinal")),
                parseInteger(metadata.get("pageNumber")),
                parseIntegers(metadata.get("pageNumbers")),
                parsePipeList(metadata.get("captions")));
    }

    private String summarize(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 280 ? normalized : normalized.substring(0, 277) + "...";
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(Objects.toString(value));
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private String composeQuery(String query, String topicHint, String tagHint) {
        var builder = new StringBuilder(query == null ? "" : query);
        if (topicHint != null && !topicHint.isBlank()) {
            builder.append(" topic:").append(topicHint);
        }
        if (tagHint != null && !tagHint.isBlank()) {
            builder.append(" tag:").append(tagHint);
        }
        return builder.toString().trim();
    }

    private List<String> parseTags(Object value) {
        return parseCommaList(value);
    }

    private List<String> parseCommaList(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private List<String> parsePipeList(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(String.valueOf(value).split("\\|"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private List<Integer> parseIntegers(Object value) {
        return parseCommaList(value).stream().map(this::parseInteger).filter(Objects::nonNull).toList();
    }

    private String escape(String value) {
        return value.replace("'", "\\'");
    }
}
