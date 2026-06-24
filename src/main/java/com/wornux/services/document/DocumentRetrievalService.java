package com.wornux.services.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wornux.config.DocumentIngestionProperties;
import com.wornux.dtos.document.DocumentContextResult;
import com.wornux.dtos.document.DocumentSearchHit;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

@Service
public class DocumentRetrievalService {

    private final VectorStore vectorStore;
    private final DocumentIngestionProperties properties;

    public DocumentRetrievalService(VectorStore vectorStore, DocumentIngestionProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public DocumentContextResult search(
            UUID groupClassId,
            String query,
            String ingestionIdHint,
            String filenameHint,
            String topicHint,
            String tagHint) {
        if (groupClassId == null) {
            return new DocumentContextResult(List.of(), false);
        }

        var request = SearchRequest.builder()
                .query(composeQuery(query, topicHint, tagHint))
                .topK(properties.getRetrievalTopK())
                .similarityThreshold(properties.getRetrievalSimilarityThreshold())
                .filterExpression(filter(groupClassId, ingestionIdHint, filenameHint))
                .build();

        List<DocumentSearchHit> hits = vectorStore.similaritySearch(request).stream().map(this::toSearchHit).toList();
        return new DocumentContextResult(hits, !hits.isEmpty());
    }

    private Filter.Expression filter(UUID groupClassId, String ingestionIdHint, String filenameHint) {
        var builder = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> filters = new ArrayList<>();
        filters.add(builder.eq("groupClassId", groupClassId.toString()));
        filters.add(builder.eq("status", "READY"));
        if (ingestionIdHint != null && !ingestionIdHint.isBlank()) {
            filters.add(builder.eq("ingestionId", ingestionIdHint));
        }
        if (filenameHint != null && !filenameHint.isBlank()) {
            filters.add(builder.eq("title", filenameHint));
        }

        var combined = filters.getFirst();
        for (int index = 1; index < filters.size(); index++) {
            combined = builder.and(combined, filters.get(index));
        }
        return combined.build();
    }

    private DocumentSearchHit toSearchHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String title = stringValue(metadata.get("title"));
        return new DocumentSearchHit(document.getId(),
                stringValue(metadata.get("ingestionId")),
                title,
                title,
                "",
                List.of(),
                "Document",
                summarize(document.getText()),
                document.getScore(),
                integerValue(metadata.get("chunkIndex")),
                null,
                List.of(),
                List.of());
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private String summarize(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        var normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 280 ? normalized : normalized.substring(0, 277) + "...";
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
}
