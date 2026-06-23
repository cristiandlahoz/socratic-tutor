package com.wornux.services.document;

import java.util.List;
import java.util.UUID;

import com.wornux.config.DocumentIngestionProperties;
import com.wornux.dtos.document.DocumentContextResult;
import com.wornux.dtos.document.DocumentSearchHit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class DocumentRetrievalService {

    private final EmbeddingModel embeddingModel;
    private final EntityManager entityManager;
    private final DocumentIngestionProperties properties;

    public DocumentRetrievalService(EmbeddingModel embeddingModel, EntityManager entityManager, DocumentIngestionProperties properties) {
        this.embeddingModel = embeddingModel;
        this.entityManager = entityManager;
        this.properties = properties;
    }

    public DocumentContextResult search(
            UUID groupClassId,
            String query,
            String documentIdHint,
            String filenameHint,
            String topicHint,
            String tagHint) {
        if (groupClassId == null) {
            return new DocumentContextResult(List.of(), false);
        }

        var queryEmbedding = embeddingModel.embed(composeQuery(query, topicHint, tagHint));

        var sql = new StringBuilder("""
            SELECT c.id, c.content, c.chunk_index, d.id, d.title,
                   c.embedding <=> CAST(:queryVec AS vector) AS distance
            FROM grounding_chunk c
            JOIN grounding_document d ON d.id = c.document_id
            JOIN grounding_collection col ON col.id = d.collection_id
            WHERE col.group_class_id = CAST(:groupClassId AS uuid)
              AND c.active = true
              AND c.embedding IS NOT NULL
            """);

        if (documentIdHint != null && !documentIdHint.isBlank()) {
            sql.append(" AND d.id = :documentId");
        }
        if (filenameHint != null && !filenameHint.isBlank()) {
            sql.append(" AND d.title = :filename");
        }

        sql.append("""
             AND c.embedding <=> CAST(:queryVec AS vector) <= :maxDistance
             ORDER BY distance
             LIMIT :topK
            """);

        Query nativeQuery = entityManager.createNativeQuery(sql.toString());
        var vectorStr = vectorToString(queryEmbedding);
        nativeQuery.setParameter("queryVec", vectorStr);
        nativeQuery.setParameter("groupClassId", groupClassId.toString());
        nativeQuery.setParameter("maxDistance", 1.0 - properties.getRetrievalSimilarityThreshold());
        nativeQuery.setParameter("topK", properties.getRetrievalTopK());

        if (documentIdHint != null && !documentIdHint.isBlank()) {
            nativeQuery.setParameter("documentId", Long.parseLong(documentIdHint));
        }
        if (filenameHint != null && !filenameHint.isBlank()) {
            nativeQuery.setParameter("filename", filenameHint);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = nativeQuery.getResultList();

        List<DocumentSearchHit> hits = rows.stream()
                .map(this::toSearchHit)
                .toList();
        return new DocumentContextResult(hits, !hits.isEmpty());
    }

    private DocumentSearchHit toSearchHit(Object[] row) {
        var chunkId = longValue(row[0]);
        var content = (String) row[1];
        var ordinal = row[2] instanceof Number n ? n.intValue() : null;
        var documentId = longValue(row[3]);
        var filename = (String) row[4];
        var distance = row[5] instanceof Number n ? n.doubleValue() : 0.0;
        var score = 1.0 - distance;

        return new DocumentSearchHit(
                chunkId,
                documentId,
                filename,
                filename,
                "",
                List.of(),
                "Document",
                summarize(content),
                score,
                ordinal,
                null,
                List.of(),
                List.of());
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(String.valueOf(value));
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

    private String vectorToString(float[] vector) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
