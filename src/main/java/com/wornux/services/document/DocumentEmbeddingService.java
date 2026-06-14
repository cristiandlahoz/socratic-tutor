package com.wornux.services.document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wornux.data.entities.*;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class DocumentEmbeddingService {

    private final VectorStore vectorStore;

    public DocumentEmbeddingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void reindex(com.wornux.data.entities.Document document, List<DocumentSegment> segments) {
        if (segments.isEmpty()) {
            return;
        }

        deleteSegments(segments);
        vectorStore.add(
            segments.stream()
                    .map(
                        segment -> new org.springframework.ai.document.Document(segment.getId().toString(),
                                segment.getContent(),
                                metadata(document, segment)))
                    .toList());
    }

    public void deleteSegments(List<DocumentSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        vectorStore.delete(segments.stream().map(segment -> segment.getId().toString()).toList());
    }

    private Map<String, Object> metadata(com.wornux.data.entities.Document document, DocumentSegment segment) {
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
