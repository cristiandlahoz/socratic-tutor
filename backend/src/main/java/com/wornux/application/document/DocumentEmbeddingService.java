package com.wornux.application.document;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.document.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
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
    metadata.put(
        "documentTitle", document.getCatalogTitle() == null ? "" : document.getCatalogTitle());
    metadata.put(
        "documentTopic", document.getCatalogTopic() == null ? "" : document.getCatalogTopic());
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
