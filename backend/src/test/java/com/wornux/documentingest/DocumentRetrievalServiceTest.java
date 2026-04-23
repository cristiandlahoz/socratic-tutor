package com.wornux.documentingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

class DocumentRetrievalServiceTest {

  @Test
  void search_filters_hits_by_client_id() {
    var owner = UUID.randomUUID();
    var stranger = UUID.randomUUID();
    var store =
        new InMemoryVectorStore(
            List.of(
                new Document(
                    "segment-1",
                    "Busqueda binaria con complejidad logaritmica.",
                    Map.of(
                        "clientId",
                        owner.toString(),
                        "documentId",
                        UUID.randomUUID().toString(),
                        "filename",
                        "report.pdf",
                        "documentTitle",
                        "Reporte de algoritmos",
                        "documentTopic",
                        "Busqueda binaria",
                        "documentTags",
                        "algoritmos,busqueda",
                        "headingPath",
                        "Reporte",
                        "pageNumbers",
                        "3,4",
                        "captions",
                        "Tabla 1 | Figura 2",
                        "segmentOrdinal",
                        1)),
                new Document(
                    "segment-2",
                    "Documento de otro cliente.",
                    Map.of(
                        "clientId",
                        stranger.toString(),
                        "documentId",
                        UUID.randomUUID().toString(),
                        "filename",
                        "secret.pdf",
                        "documentTitle",
                        "Privado",
                        "documentTopic",
                        "Otro cliente",
                        "documentTags",
                        "privado",
                        "headingPath",
                        "Privado",
                        "segmentOrdinal",
                        1))));
    var properties = new DocumentIngestionProperties();
    properties.setRetrievalSimilarityThreshold(0.0);
    var service = new DocumentRetrievalService(store, properties);

    var result = service.search(owner, "busqueda binaria", null, null, null, null);

    assertThat(result.contextFound()).isTrue();
    assertThat(result.hits()).hasSize(1);
    assertThat(result.hits().getFirst().filename()).isEqualTo("report.pdf");
    assertThat(result.hits().getFirst().documentTitle()).isEqualTo("Reporte de algoritmos");
    assertThat(result.hits().getFirst().documentTags()).contains("algoritmos", "busqueda");
    assertThat(result.hits().getFirst().pageNumbers()).containsExactly(3, 4);
    assertThat(result.hits().getFirst().captions()).containsExactly("Tabla 1", "Figura 2");
  }

  private static final class InMemoryVectorStore implements VectorStore {

    private final List<Document> documents;

    private InMemoryVectorStore(List<Document> documents) {
      this.documents = new ArrayList<>(documents);
    }

    @Override
    public void add(List<Document> documents) {
      this.documents.addAll(documents);
    }

    @Override
    public void delete(List<String> idList) {
      documents.removeIf(document -> idList.contains(document.getId()));
    }

    @Override
    public void delete(Filter.Expression expression) {}

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
      String query = request.getQuery().toLowerCase();
      String filter =
          Optional.ofNullable(request.getFilterExpression()).map(Object::toString).orElse("");
      String clientId = extractQuotedValue(filter, "clientId");
      return documents.stream()
          .filter(
              document ->
                  clientId == null || clientId.equals(document.getMetadata().get("clientId")))
          .filter(document -> document.getText().toLowerCase().contains(query.split("\\s+")[0]))
          .toList();
    }

    @Override
    public <T> Optional<T> getNativeClient() {
      return Optional.empty();
    }

    private String extractQuotedValue(String filter, String key) {
      int keyIndex = filter.indexOf(key);
      if (keyIndex < 0) {
        return null;
      }
      int firstQuote = filter.indexOf('\'', keyIndex);
      int secondQuote = filter.indexOf('\'', firstQuote + 1);
      if (firstQuote < 0 || secondQuote < 0) {
        return null;
      }
      return filter.substring(firstQuote + 1, secondQuote);
    }
  }
}
