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

    private final VectorStore vectorStore;

    public DocumentVectorIndexingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<String> index(
            UUID groupClassId,
            UUID createdByGroupClassMemberId,
            UUID ingestionId,
            String title,
            List<EditableSegmentViewModel> segments) {
        List<Document> documents = segments.stream()
                .filter(segment -> segment.content() != null && !segment.content().isBlank())
                .map(
                    segment -> Document.builder()
                            .id(UUID.randomUUID().toString())
                            .text(segment.content())
                            .metadata(
                                Map.of(
                                    "groupClassId",
                                    groupClassId.toString(),
                                    "createdByGroupClassMemberId",
                                    createdByGroupClassMemberId.toString(),
                                    "ingestionId",
                                    ingestionId.toString(),
                                    "title",
                                    title,
                                    "status",
                                    "READY",
                                    "chunkIndex",
                                    segment.ordinal()))
                            .build())
                .toList();

        vectorStore.add(documents);
        return documents.stream().map(Document::getId).toList();
    }

    public void delete(List<String> vectorIds) {
        if (vectorIds != null && !vectorIds.isEmpty()) {
            vectorStore.delete(vectorIds);
        }
    }
}
