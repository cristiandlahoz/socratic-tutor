package com.wornux.services.document;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.wornux.config.DocumentIngestionProperties;
import com.wornux.data.entities.Document;
import com.wornux.data.entities.DocumentIngestionJob;
import com.wornux.data.entities.DocumentSegment;
import com.wornux.data.enums.DocumentIngestionStage;
import com.wornux.data.enums.DocumentStatus;
import com.wornux.data.repositories.document.DocumentIngestionJobRepository;
import com.wornux.data.repositories.document.DocumentRepository;
import com.wornux.data.repositories.document.DocumentSegmentRepository;
import com.wornux.dtos.document.DocumentCatalogEntry;
import com.wornux.dtos.document.DocumentIngestionException;
import com.wornux.infrastructure.external.docling.DoclingClientService;
import com.wornux.ui.ingestion.EditableSegmentViewModel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

  @Mock DocumentRepository documentRepository;
  @Mock DocumentSegmentRepository segmentRepository;
  @Mock DocumentIngestionJobRepository jobRepository;
  @Mock DoclingClientService doclingClientService;
  @Mock DocumentCatalogService catalogService;
  @Mock DocumentEmbeddingService embeddingService;
  @Mock DocumentIngestionProperties properties;

  @InjectMocks DocumentIngestionService service;

  @Test
  void rejectsNonPdfBeforePersistingAnything() {
    var command = new StartIngestionCommand(UUID.randomUUID(), "notes.txt", "text/plain", new byte[] {1, 2, 3});

    assertThatThrownBy(() -> service.startIngestion(command))
        .isInstanceOf(DocumentIngestionException.class)
        .hasMessageContaining("solo acepto archivos PDF");

    verifyNoInteractions(documentRepository, segmentRepository, jobRepository, doclingClientService);
  }

  @Test
  void marksDocumentFailedAndDeletesVectorsWhenEmbeddingFails() {
    var clientId = UUID.randomUUID();
    var document = document(clientId);
    var segment = segment(document);
    var job = DocumentIngestionJob.start(document, "review");
    var command = approveCommand(clientId, document.getId(), segment);

    when(documentRepository.findByIdAndClientId(document.getId(), clientId))
        .thenReturn(Optional.of(document));
    when(jobRepository.findFirstByDocument_IdOrderByStartedAtDesc(document.getId()))
        .thenReturn(Optional.of(job));
    when(segmentRepository.findByDocument_IdOrderByOrdinalAsc(document.getId()))
        .thenReturn(List.of(segment));
    when(catalogService.analyzeOrFallback(document.getOriginalFilename(), command.reviewedMarkdown()))
        .thenReturn(new DocumentCatalogService.CatalogAnalysis(catalog(), false));
    doThrow(new IllegalStateException("embedding down"))
        .when(embeddingService)
        .reindex(document, List.of(segment));

    assertThatThrownBy(() -> service.approve(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("embedding down");

    verify(embeddingService).deleteSegments(List.of(segment));
    verify(documentRepository, atLeastOnce()).save(document);
    verify(jobRepository, atLeastOnce()).save(job);
    org.assertj.core.api.Assertions.assertThat(document.status()).isEqualTo(DocumentStatus.FAILED);
    org.assertj.core.api.Assertions.assertThat(job.stage()).isEqualTo(DocumentIngestionStage.FAILED);
  }

  @Test
  void deletesVectorsBeforeDeletingDocument() {
    var clientId = UUID.randomUUID();
    var document = document(clientId);
    var segment = segment(document);

    when(documentRepository.findByIdAndClientId(document.getId(), clientId))
        .thenReturn(Optional.of(document));
    when(segmentRepository.findByDocument_IdOrderByOrdinalAsc(document.getId()))
        .thenReturn(List.of(segment));

    service.delete(clientId, document.getId());

    var ordered = inOrder(embeddingService, documentRepository);
    ordered.verify(embeddingService).deleteSegments(List.of(segment));
    ordered.verify(documentRepository).delete(document);
  }

  @Test
  void deletesSegmentsExcludedFromReviewBeforeIndexing() {
    var clientId = UUID.randomUUID();
    var document = document(clientId);
    var keptSegment = segment(document);
    var removedSegment = segment(document, 2, "This duplicate chunk should be removed.");
    var job = DocumentIngestionJob.start(document, "review");
    var command = approveCommand(clientId, document.getId(), keptSegment);

    when(documentRepository.findByIdAndClientId(document.getId(), clientId))
        .thenReturn(Optional.of(document));
    when(jobRepository.findFirstByDocument_IdOrderByStartedAtDesc(document.getId()))
        .thenReturn(Optional.of(job));
    when(segmentRepository.findByDocument_IdOrderByOrdinalAsc(document.getId()))
        .thenReturn(List.of(keptSegment, removedSegment), List.of(keptSegment));
    when(catalogService.analyzeOrFallback(document.getOriginalFilename(), command.reviewedMarkdown()))
        .thenReturn(new DocumentCatalogService.CatalogAnalysis(catalog(), false));

    service.approve(command);

    verify(segmentRepository).deleteAll(List.of(removedSegment));
    verify(segmentRepository).saveAll(List.of(keptSegment));
    verify(embeddingService).reindex(document, List.of(keptSegment));
  }

  private Document document(UUID clientId) {
    var document = Document.create(
        new StartIngestionCommand(clientId, "algorithms.pdf", "application/pdf", "%PDF".getBytes()),
        "0".repeat(64));
    document.markReviewReady("# Algorithms", 1);
    return document;
  }

  private DocumentSegment segment(Document document) {
    return segment(document, 1, "Binary search halves the search interval.");
  }

  private DocumentSegment segment(Document document, int ordinal, String content) {
    return DocumentSegment.createDraft(
        document,
        ordinal,
        "Algorithms",
        content,
        6,
        1,
        List.of(1),
        List.of(),
        List.of(),
        content);
  }

  private ApproveDocumentCommand approveCommand(UUID clientId, UUID documentId, DocumentSegment segment) {
    return new ApproveDocumentCommand(
        clientId,
        documentId,
        "# Algorithms\nBinary search halves the search interval.",
        List.of(new EditableSegmentViewModel(
            segment.getId(),
            1,
            "Algorithms",
            segment.getContent(),
            false,
            false,
            segment.getCharCount(),
            segment.getTokenCount(),
            segment.getPageNumber(),
            segment.getSourcePageNumbers(),
            segment.getCaptions(),
            segment.getDocItems(),
            segment.getRawText(),
            segment.getChunker())));
  }

  private DocumentCatalogEntry catalog() {
    return new DocumentCatalogEntry(
        "Algorithms", "search algorithms", "Binary search basics", List.of("search"), List.of(), List.of());
  }
}
