package com.wornux.documentingest;

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
  private final FilterExpressionTextParser filterExpressionTextParser =
      new FilterExpressionTextParser();

  public DocumentRetrievalService(VectorStore vectorStore, DocumentIngestionProperties properties) {
    this.vectorStore = vectorStore;
    this.properties = properties;
  }

  public DocumentContextResult search(UUID clientId, String query, String filenameHint) {
    String filterExpression = "clientId == '%s'".formatted(clientId);
    if (filenameHint != null && !filenameHint.isBlank()) {
      filterExpression += " && filename == '%s'".formatted(escape(filenameHint));
    }

    var request =
        SearchRequest.builder()
            .query(query)
            .topK(properties.getRetrievalTopK())
            .similarityThreshold(properties.getRetrievalSimilarityThreshold())
            .filterExpression(filterExpressionTextParser.parse(filterExpression))
            .build();

    List<DocumentSearchHit> hits =
        vectorStore.similaritySearch(request).stream()
            .map(
                document ->
                    toSearchHit(
                        document.getId(),
                        document.getText(),
                        document.getMetadata(),
                        document.getScore()))
            .toList();
    return new DocumentContextResult(hits, !hits.isEmpty());
  }

  private DocumentSearchHit toSearchHit(
      String segmentId, String content, Map<String, Object> metadata, Double score) {
    return new DocumentSearchHit(
        segmentId,
        UUID.fromString(String.valueOf(metadata.get("documentId"))),
        String.valueOf(metadata.getOrDefault("filename", "Documento PDF")),
        String.valueOf(metadata.getOrDefault("headingPath", "Documento")),
        summarize(content),
        score,
        parseInteger(metadata.get("segmentOrdinal")),
        parseInteger(metadata.get("pageNumber")));
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
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private String escape(String value) {
    return value.replace("'", "\\'");
  }
}
