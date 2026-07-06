package com.wornux.services.document;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.wornux.config.DocumentIngestionProperties;
import com.wornux.dtos.document.DocumentContextResult;
import com.wornux.dtos.document.DocumentPageResult;
import com.wornux.dtos.document.DocumentSearchHit;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DocumentRetrievalService {

    private static final int PREVIEW_MAX_CHARS = 600;
    private static final int DEFAULT_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 3;

    private final VectorStore vectorStore;
    private final JdbcClient jdbcClient;
    private final DocumentIngestionProperties properties;

    public DocumentRetrievalService(
            VectorStore vectorStore,
            JdbcClient jdbcClient,
            DocumentIngestionProperties properties) {
        this.vectorStore = vectorStore;
        this.jdbcClient = jdbcClient;
        this.properties = properties;
    }

    public DocumentContextResult search(UUID groupClassId, String query) {
        if (groupClassId == null) {
            return new DocumentContextResult(List.of(), false);
        }

        var request = SearchRequest.builder()
                .query(query == null ? "" : query.trim())
                .topK(properties.getRetrievalTopK())
                .similarityThreshold(properties.getRetrievalSimilarityThreshold())
                .filterExpression(searchFilter(groupClassId))
                .build();

        List<DocumentSearchHit> hits = vectorStore.similaritySearch(request)
                .stream()
                .map(this::toSearchHit)
                .flatMap(Optional::stream)
                .toList();
        return new DocumentContextResult(hits, !hits.isEmpty());
    }

    public DocumentPageResult readPage(UUID groupClassId, String cursor, Integer pageSize) {
        if (groupClassId == null) {
            return emptyPage();
        }

        var decodedCursor = decodeCursor(cursor);
        if (decodedCursor.isEmpty()) {
            return emptyPage();
        }

        int requestedPageSize = normalizedPageSize(pageSize);
        var documentCursor = decodedCursor.get();
        var chunks = jdbcClient
                .sql("""
                     select content, metadata ->> 'title' as source, (metadata ->> 'chunkIndex')::int as chunk_index
                     from grounding_vector_store
                     where metadata ->> 'groupClassId' = :groupClassId
                       and metadata ->> 'ingestionId' = :ingestionId
                       and metadata ->> 'status' = 'READY'
                       and (metadata ->> 'chunkIndex')::int >= :chunkIndex
                     order by (metadata ->> 'chunkIndex')::int
                     limit :limit
                     """)
                .param("groupClassId", groupClassId.toString())
                .param("ingestionId", documentCursor.ingestionId().toString())
                .param("chunkIndex", documentCursor.chunkIndex())
                .param("limit", requestedPageSize + 1)
                .query(
                    (rs, _) -> new DocumentChunk(rs.getString("source"),
                            rs.getString("content"),
                            rs.getInt("chunk_index")))
                .list();

        if (chunks.isEmpty()) {
            return emptyPage();
        }

        boolean hasNext = chunks.size() > requestedPageSize;
        var visibleChunks = hasNext ? chunks.subList(0, requestedPageSize) : chunks;
        var source = visibleChunks.getFirst().source();
        var content = joinContent(visibleChunks);
        var previousChunkIndex = documentCursor.chunkIndex() - requestedPageSize;
        var previousCursor = previousChunkIndex < 0
                ? null
                : encodeCursor(new DocumentCursor(documentCursor.ingestionId(), previousChunkIndex));
        var lastVisibleChunkIndex = visibleChunks.getLast().chunkIndex();
        var nextCursor = hasNext
                ? encodeCursor(new DocumentCursor(documentCursor.ingestionId(), lastVisibleChunkIndex + 1))
                : null;

        return new DocumentPageResult(source, content, previousCursor, nextCursor, previousCursor != null, hasNext);
    }

    private Filter.Expression searchFilter(UUID groupClassId) {
        var builder = new FilterExpressionBuilder();
        return builder.and(builder.eq("groupClassId", groupClassId.toString()), builder.eq("status", "READY")).build();
    }

    private Optional<DocumentSearchHit> toSearchHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        var ingestionId = uuidValue(metadata.get("ingestionId"));
        var chunkIndex = integerValue(metadata.get("chunkIndex"));
        if (ingestionId == null || chunkIndex == null) {
            return Optional.empty();
        }
        return Optional.of(
            new DocumentSearchHit(stringValue(metadata.get("title")),
                    preview(document.getText()),
                    encodeCursor(new DocumentCursor(ingestionId, chunkIndex))));
    }

    private DocumentPageResult emptyPage() {
        return new DocumentPageResult(null, "", null, null, false, false);
    }

    private int normalizedPageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
    }

    private String joinContent(List<DocumentChunk> chunks) {
        List<String> sections = new ArrayList<>();
        for (var chunk : chunks) {
            sections.add(normalize(chunk.content()));
        }
        return String.join("\n\n", sections).trim();
    }

    private String preview(String content) {
        var normalized = normalize(content);
        return normalized.length() <= PREVIEW_MAX_CHARS
                ? normalized
                : normalized.substring(0, PREVIEW_MAX_CHARS - 3) + "...";
    }

    private String normalize(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content.replaceAll("\\s+", " ").trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private UUID uuidValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private String encodeCursor(DocumentCursor cursor) {
        var value = "%s:%d".formatted(cursor.ingestionId(), cursor.chunkIndex());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Optional<DocumentCursor> decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            var decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            var separator = decoded.lastIndexOf(':');
            if (separator < 0) {
                return Optional.empty();
            }
            return Optional.of(
                new DocumentCursor(UUID.fromString(decoded.substring(0, separator)),
                        Integer.parseInt(decoded.substring(separator + 1))));
        }
        catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private record DocumentCursor(UUID ingestionId, int chunkIndex) {}

    private record DocumentChunk(String source, String content, int chunkIndex) {}
}
