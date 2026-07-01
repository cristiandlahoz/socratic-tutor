package com.wornux.services.document;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wornux.ui.ingestion.EditableSegmentViewModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class DocumentVectorIndexingService {

    private static final String READY = "READY";

    private final VectorStore vectorStore;

    public DocumentVectorIndexingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<String> index(
            UUID groupClassId,
            UUID createdByGroupClassMemberId,
            UUID ingestionId,
            String title,
            CourseMaterialCatalog catalog,
            List<EditableSegmentViewModel> segments) {
        var documents = documentsFor(groupClassId, createdByGroupClassMemberId, ingestionId, title, catalog, segments);
        vectorStore.add(documents);
        return documents.stream().map(Document::getId).toList();
    }

    public void delete(List<String> vectorIds) {
        if (vectorIds != null && !vectorIds.isEmpty()) {
            vectorStore.delete(vectorIds);
        }
    }

    private List<Document> documentsFor(
            UUID groupClassId,
            UUID createdByGroupClassMemberId,
            UUID ingestionId,
            String title,
            CourseMaterialCatalog catalog,
            List<EditableSegmentViewModel> segments) {
        return segments.stream()
                .filter(this::hasContent)
                .map(segment -> documentFor(
                    groupClassId,
                    createdByGroupClassMemberId,
                    ingestionId,
                    title,
                    catalog,
                    segment))
                .toList();
    }

    private Document documentFor(
            UUID groupClassId,
            UUID createdByGroupClassMemberId,
            UUID ingestionId,
            String title,
            CourseMaterialCatalog catalog,
            EditableSegmentViewModel segment) {
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .text(segment.content())
                .metadata(metadataFor(groupClassId, createdByGroupClassMemberId, ingestionId, title, catalog, segment))
                .build();
    }

    private Map<String, Object> metadataFor(
            UUID groupClassId,
            UUID createdByGroupClassMemberId,
            UUID ingestionId,
            String title,
            CourseMaterialCatalog catalog,
            EditableSegmentViewModel segment) {
        return Map.of(
            "groupClassId", groupClassId.toString(),
            "createdByGroupClassMemberId", createdByGroupClassMemberId.toString(),
            "ingestionId", ingestionId.toString(),
            "title", title,
            "status", READY,
            "catalog", catalogFor(catalog),
            "chunkIndex", segment.ordinal());
    }

    private Map<String, Object> catalogFor(CourseMaterialCatalog catalog) {
        return Map.of("label", catalog.label(), "useWhen", catalog.useWhen(), "aliases", catalog.aliases());
    }

    private boolean hasContent(EditableSegmentViewModel segment) {
        return segment.content() != null && !segment.content().isBlank();
    }
}
