package com.wornux.documentingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentCatalogPromptServiceTest {

  @Test
  void buildInventoryPrompt_includes_only_indexed_documents_for_current_client() {
    var repository = mock(DocumentJpaRepository.class);
    var clientId = UUID.randomUUID();
    var document =
        DocumentEntity.create(
            new StartIngestionCommand(
                clientId, "report.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8)),
            "checksum");
    document.applyCatalog(
        new DocumentCatalogEntry(
            "Reporte de busqueda",
            "Explica busqueda binaria",
            "Resumen",
            List.of("algoritmos", "busqueda"),
            List.of("busqueda binaria"),
            List.of("Cual es la complejidad?")),
        false);
    document.markIndexed("# reporte");

    when(repository.findByClientIdAndStatusOrderByUpdatedAtDescCreatedAtDesc(
            clientId, DocumentStatus.INDEXED.name()))
        .thenReturn(List.of(document));

    var service = new DocumentCatalogPromptService(repository, new DocumentIngestionProperties());

    var prompt = service.buildInventoryPrompt(clientId);

    assertThat(prompt).contains("report.pdf");
    assertThat(prompt).contains("Explica busqueda binaria");
    assertThat(prompt).contains("algoritmos, busqueda");
    assertThat(prompt).contains("Cual es la complejidad?");
  }
}
