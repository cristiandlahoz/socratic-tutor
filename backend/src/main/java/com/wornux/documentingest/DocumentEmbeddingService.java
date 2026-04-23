package com.wornux.documentingest;

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
                segment ->
                    new Document(
                        segment.getId().toString(),
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
    metadata.put("segmentOrdinal", segment.getOrdinal());
    metadata.put("headingPath", segment.getHeadingPath() == null ? "" : segment.getHeadingPath());
    metadata.put("contentType", "pdf_segment");
    if (segment.getPageNumber() != null) {
      metadata.put("pageNumber", segment.getPageNumber());
    }
    return metadata;
  }
}
