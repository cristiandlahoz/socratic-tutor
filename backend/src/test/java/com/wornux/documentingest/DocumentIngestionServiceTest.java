package com.wornux.documentingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentIngestionServiceTest {

  private final DocumentJpaRepository documentRepository = mock(DocumentJpaRepository.class);
  private final DocumentSegmentJpaRepository segmentRepository =
      mock(DocumentSegmentJpaRepository.class);
  private final DocumentIngestionJobJpaRepository jobRepository =
      mock(DocumentIngestionJobJpaRepository.class);
  private final DoclingClientService doclingClientService = mock(DoclingClientService.class);
  private final DocumentEmbeddingService embeddingService = mock(DocumentEmbeddingService.class);

  private final Map<UUID, DocumentEntity> documents = new LinkedHashMap<>();
  private final Map<UUID, DocumentIngestionJobEntity> jobs = new LinkedHashMap<>();
  private final Map<UUID, List<DocumentSegmentEntity>> segmentsByDocumentId = new LinkedHashMap<>();

  private DocumentIngestionService service;

  @BeforeEach
  void setUp() {
    var properties = new DocumentIngestionProperties();
    service =
        new DocumentIngestionService(
            documentRepository,
            segmentRepository,
            jobRepository,
            doclingClientService,
            new DocumentSegmentationService(properties),
            embeddingService,
            properties);

    doAnswer(
            invocation -> {
              DocumentEntity document = invocation.getArgument(0);
              documents.put(document.getId(), document);
              return document;
            })
        .when(documentRepository)
        .save(any(DocumentEntity.class));
    when(documentRepository.findByIdAndClientId(any(UUID.class), any(UUID.class)))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(documents.get(invocation.getArgument(0)))
                    .filter(document -> document.getClientId().equals(invocation.getArgument(1))));
    when(documentRepository.findFirstByClientIdOrderByUpdatedAtDesc(any(UUID.class)))
        .thenAnswer(
            invocation ->
                documents.values().stream()
                    .filter(document -> document.getClientId().equals(invocation.getArgument(0)))
                    .findFirst());

    doAnswer(
            invocation -> {
              DocumentIngestionJobEntity job = invocation.getArgument(0);
              jobs.put(job.getId(), job);
              return job;
            })
        .when(jobRepository)
        .save(any(DocumentIngestionJobEntity.class));
    when(jobRepository.findFirstByDocument_IdOrderByStartedAtDesc(any(UUID.class)))
        .thenAnswer(
            invocation ->
                jobs.values().stream()
                    .filter(job -> job.getDocument().getId().equals(invocation.getArgument(0)))
                    .findFirst());

    doAnswer(
            invocation -> {
              UUID documentId = invocation.getArgument(0);
              segmentsByDocumentId.remove(documentId);
              return null;
            })
        .when(segmentRepository)
        .deleteByDocument_Id(any(UUID.class));
    doAnswer(
            invocation -> {
              List<DocumentSegmentEntity> segments = invocation.getArgument(0);
              if (!segments.isEmpty()) {
                segmentsByDocumentId.put(
                    segments.getFirst().getDocument().getId(), new ArrayList<>(segments));
              }
              return segments;
            })
        .when(segmentRepository)
        .saveAll(anyList());
    when(segmentRepository.findByDocument_IdOrderByOrdinalAsc(any(UUID.class)))
        .thenAnswer(
            invocation ->
                new ArrayList<>(
                    segmentsByDocumentId.getOrDefault(invocation.getArgument(0), List.of())));
  }

  @Test
  void startIngestion_creates_reviewable_markdown_and_segments() {
    var clientId = UUID.randomUUID();
    when(doclingClientService.convertPdfToMarkdown(anyString(), any()))
        .thenReturn(
            new DoclingConversionResult(
                """
                # Tema

                Este PDF habla sobre estructuras repetitivas.
                """,
                null));

    var review =
        service.startIngestion(
            new StartIngestionCommand(
                clientId,
                "notes.pdf",
                "application/pdf",
                "%PDF-1.4 ok".getBytes(StandardCharsets.UTF_8)));

    assertThat(review.filename()).isEqualTo("notes.pdf");
    assertThat(review.markdown()).contains("estructuras repetitivas");
    assertThat(review.segments()).isNotEmpty();
    assertThat(review.stageLabel()).contains("Revisa");
    assertThat(service.loadLatestReview(clientId)).isPresent();
  }

  @Test
  void approve_marks_document_as_indexed_and_sends_unique_segments_to_embedding_service() {
    var clientId = UUID.randomUUID();
    when(doclingClientService.convertPdfToMarkdown(anyString(), any()))
        .thenReturn(
            new DoclingConversionResult(
                """
                # Tema

                Segmento duplicado.

                Segmento duplicado.
                """,
                null));

    var review =
        service.startIngestion(
            new StartIngestionCommand(
                clientId,
                "dup.pdf",
                "application/pdf",
                "%PDF-1.4 ok".getBytes(StandardCharsets.UTF_8)));

    var reviewedSegments =
        review.segments().stream()
            .map(segment -> segment.withContent("Segmento duplicado."))
            .toList();

    var indexed =
        service.approve(
            new ApproveDocumentCommand(
                clientId, review.documentId(), review.markdown(), reviewedSegments));

    assertThat(indexed.indexed()).isTrue();
    assertThat(documents.get(review.documentId()).status()).isEqualTo(DocumentStatus.INDEXED);

    ArgumentCaptor<List<DocumentSegmentEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(embeddingService).reindex(any(DocumentEntity.class), captor.capture());
    assertThat(captor.getValue()).hasSize(1);
  }
}
