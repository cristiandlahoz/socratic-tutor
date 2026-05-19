package com.wornux.application.document;

import com.wornux.domain.document.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class DocumentEmbeddingService {

    private final VectorStore vectorStore;

    public DocumentEmbeddingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void reindex(DocumentEntity document, List<DocumentSegmentEntity> segments) {
        if (segments.isEmpty()) {
            return;
        }

        vectorStore.delete(segments.stream().map(segment -> segment.getId().toString()).toList());
        vectorStore.add(
            segments.stream()
                    .map(
                        segment -> new Document(segment.getId().toString(),
                                segment.getContent(),
                                metadata(document, segment)))
                    .toList());
    }

    private Map<String, Object> metadata(DocumentEntity document, DocumentSegmentEntity segment) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("clientId", document.getClientId().toString());
        metadata.put("documentId", document.getId().toString());
        metadata.put("segmentId", segment.getId().toString());
        metadata.put("filename", document.getOriginalFilename());
        metadata.put("documentTitle", document.getCatalogTitle() == null ? "" : document.getCatalogTitle());
        metadata.put("documentTopic", document.getCatalogTopic() == null ? "" : document.getCatalogTopic());
        var tags = document.getCatalogTags() == null ? List.<String>of() : document.getCatalogTags();
        metadata.put("documentTags", tags.isEmpty() ? "" : String.join(",", tags));
        metadata.put("segmentOrdinal", segment.getOrdinal());
        metadata.put("headingPath", segment.getHeadingPath() == null ? "" : segment.getHeadingPath());
        metadata.put("chunker", segment.getChunker());
        metadata.put("pageNumbers", joinIntegers(segment.getSourcePageNumbers()));
        metadata.put("captions", joinStrings(segment.getCaptions()));
        metadata.put("docItems", joinStrings(segment.getDocItems()));
        metadata.put("contentType", "pdf_segment");
        if (segment.getPageNumber() != null) {
            metadata.put("pageNumber", segment.getPageNumber());
        }
        return metadata;
    }

    private String joinStrings(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(" | ", values);
    }

    private String joinIntegers(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
}
