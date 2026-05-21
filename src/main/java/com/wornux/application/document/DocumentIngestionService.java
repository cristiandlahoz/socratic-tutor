package com.wornux.application.document;

import com.wornux.ai.document.DocumentIngestionProperties;
import com.wornux.application.document.port.DocumentIngestionPersistencePort;
import com.wornux.domain.document.DocumentEntity;
import com.wornux.domain.document.DocumentIngestionException;
import com.wornux.domain.document.DocumentIngestionJobEntity;
import com.wornux.domain.document.DocumentIngestionStage;
import com.wornux.domain.document.DocumentSegmentEntity;
import com.wornux.domain.document.DocumentStatus;
import com.wornux.infrastructure.external.docling.DoclingClientService;
import com.wornux.presentation.documentingest.DocumentReviewVm;
import com.wornux.presentation.documentingest.EditableSegmentVm;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionService {

  private final DocumentIngestionPersistencePort documentPersistencePort;
  private final DoclingClientService doclingClientService;
  private final DocumentCatalogService catalogService;
  private final DocumentEmbeddingService embeddingService;
  private final DocumentIngestionProperties properties;

  public DocumentIngestionService(
      DocumentIngestionPersistencePort documentPersistencePort,
      DoclingClientService doclingClientService,
      DocumentCatalogService catalogService,
      DocumentEmbeddingService embeddingService,
      DocumentIngestionProperties properties) {
    this.documentPersistencePort = documentPersistencePort;
    this.doclingClientService = doclingClientService;
    this.catalogService = catalogService;
    this.embeddingService = embeddingService;
    this.properties = properties;
  }

  public DocumentReviewVm startIngestion(StartIngestionCommand command) {
    validateUpload(command);

    var document =
        documentPersistencePort.saveDocument(
            DocumentEntity.create(command, sha256(command.content())));
    var job =
        documentPersistencePort.saveJob(
            DocumentIngestionJobEntity.start(document, "PDF recibido. Preparando transformacion."));

    Path tempFile = null;
    try {
      tempFile = Files.createTempFile("ingested-document-", ".pdf");
      Files.write(tempFile, command.content());

      job.advance(
          DocumentIngestionStage.DOCLING_CONVERT, "Transformando y segmentando PDF con Docling.");
      documentPersistencePort.saveJob(job);

      var conversion =
          doclingClientService.convertPdfToMarkdownAndChunks(
              command.originalFilename(), command.content());
      if (conversion.markdown() == null || conversion.markdown().isBlank()) {
        throw new DocumentIngestionException("Docling devolvio un documento vacio.");
      }
      if (conversion.segments() == null || conversion.segments().isEmpty()) {
        throw new DocumentIngestionException(
            "Docling no pudo crear segmentos utiles para este PDF.");
      }

      document.markReviewReady(conversion.markdown(), conversion.pageCount());
      var initialCatalog =
          catalogService.analyzeOrFallback(command.originalFilename(), conversion.markdown());
      document.applyCatalog(initialCatalog.entry(), initialCatalog.stale());
      documentPersistencePort.saveDocument(document);

      job.advance(DocumentIngestionStage.SEGMENT_BUILD, "Preparando segmentos de Docling.");
      documentPersistencePort.saveJob(job);

      documentPersistencePort.deleteSegmentsByDocumentId(document.getId());
      documentPersistencePort.saveAllSegments(
          conversion.segments().stream()
              .map(
                  segment ->
                      DocumentSegmentEntity.createDraft(
                          document,
                          segment.ordinal(),
                          segment.headingPath(),
                          segment.content(),
                          segment.tokenCount(),
                          segment.pageNumber(),
                          segment.pageNumbers(),
                          segment.captions(),
                          segment.docItems(),
                          segment.rawText()))
              .toList());

      job.advance(
          DocumentIngestionStage.REVIEW,
          "Revisa el markdown y valida los segmentos antes de indexar.");
      documentPersistencePort.saveJob(job);

      return toReviewVm(document, job);
    } catch (IOException exception) {
      failIngestion(document, job, "No se pudo preparar el archivo temporal del PDF.", exception);
      throw new DocumentIngestionException("No se pudo procesar el archivo subido.", exception);
    } catch (RuntimeException exception) {
      failIngestion(document, job, safeMessage(exception), exception);
      throw exception;
    } finally {
      deleteQuietly(tempFile);
    }
  }

  public DocumentReviewVm approve(ApproveDocumentCommand command) {
    var document =
        documentPersistencePort
            .findDocumentByIdAndClientId(command.documentId(), command.clientId())
            .orElseThrow(
                () ->
                    new DocumentIngestionException("No encontre ese documento para este usuario."));
    var job =
        documentPersistencePort
            .findLatestJobByDocumentId(document.getId())
            .orElseThrow(
                () ->
                    new DocumentIngestionException(
                        "No encontre el job de ingestion del documento."));

    if (document.status() == DocumentStatus.INDEXED) {
      return toReviewVm(document, job);
    }

    validateReview(command);

    try {
      job.advance(
          DocumentIngestionStage.EMBED, "Indexando segmentos para que el chat pueda buscarlos.");
      documentPersistencePort.saveJob(job);

      document.markApproved(command.reviewedMarkdown());
      var refreshedCatalog =
          catalogService.analyzeOrFallback(
              document.getOriginalFilename(), command.reviewedMarkdown());
      document.applyCatalog(refreshedCatalog.entry(), refreshedCatalog.stale());
      documentPersistencePort.saveDocument(document);

      var persistedSegments =
          documentPersistencePort
              .findSegmentsByDocumentIdOrderByOrdinalAsc(document.getId())
              .stream()
              .collect(
                  Collectors.toMap(
                      DocumentSegmentEntity::getId,
                      segment -> segment,
                      (left, _) -> left,
                      LinkedHashMap::new));

      for (EditableSegmentVm reviewedSegment : command.segments()) {
        var segment = persistedSegments.get(reviewedSegment.id());
        if (segment == null) {
          throw new DocumentIngestionException("La revision contiene un segmento desconocido.");
        }
        segment.applyReview(reviewedSegment);
      }
      documentPersistencePort.saveAllSegments(persistedSegments.values());

      List<DocumentSegmentEntity> uniqueSegments =
          deduplicateApprovedSegments(new ArrayList<>(persistedSegments.values()));
      embeddingService.reindex(document, uniqueSegments);

      document.markIndexed(command.reviewedMarkdown());
      documentPersistencePort.saveDocument(document);
      job.complete("Documento indexado. Ya puedes preguntarle al chat.");
      documentPersistencePort.saveJob(job);

      return toReviewVm(document, job);
    } catch (RuntimeException exception) {
      document.markFailed();
      documentPersistencePort.saveDocument(document);
      job.fail("La indexacion fallo.", safeMessage(exception));
      documentPersistencePort.saveJob(job);
      throw exception;
    }
  }

  public Optional<DocumentReviewVm> loadLatestReview(UUID clientId) {
    return documentPersistencePort
        .findLatestDocumentByClientId(clientId)
        .flatMap(
            document ->
                documentPersistencePort
                    .findLatestJobByDocumentId(document.getId())
                    .map(job -> toReviewVm(document, job)));
  }

  public Optional<DocumentReviewVm> loadReview(UUID clientId, UUID documentId) {
    return documentPersistencePort
        .findDocumentByIdAndClientId(documentId, clientId)
        .flatMap(
            document ->
                documentPersistencePort
                    .findLatestJobByDocumentId(document.getId())
                    .map(job -> toReviewVm(document, job)));
  }

  private DocumentReviewVm toReviewVm(DocumentEntity document, DocumentIngestionJobEntity job) {
    return new DocumentReviewVm(
        document.getId(),
        job.getId(),
        document.getOriginalFilename(),
        document.status(),
        job.getProgressLabel(),
        document.getReviewedMarkdown() == null ? "" : document.getReviewedMarkdown(),
        documentPersistencePort.findSegmentsByDocumentIdOrderByOrdinalAsc(document.getId()).stream()
            .map(DocumentSegmentEntity::toViewModel)
            .toList(),
        document.status() == DocumentStatus.INDEXED);
  }

  private void validateUpload(StartIngestionCommand command) {
    if (command.clientId() == null) {
      throw new DocumentIngestionException("No pude resolver el cliente del navegador.");
    }
    if (command.originalFilename() == null || command.originalFilename().isBlank()) {
      throw new DocumentIngestionException("El archivo necesita un nombre.");
    }
    if (!command.originalFilename().toLowerCase().endsWith(".pdf")) {
      throw new DocumentIngestionException("Por ahora solo acepto archivos PDF.");
    }
    if (command.content() == null || command.content().length == 0) {
      throw new DocumentIngestionException("El archivo esta vacio.");
    }
    if (command.content().length > properties.getMaxFileSizeBytes()) {
      throw new DocumentIngestionException("El PDF supera el tamano maximo permitido.");
    }
    if (!looksLikePdf(command.content())) {
      throw new DocumentIngestionException("El archivo no parece ser un PDF valido.");
    }
  }

  private void validateReview(ApproveDocumentCommand command) {
    if (command.reviewedMarkdown() == null || command.reviewedMarkdown().isBlank()) {
      throw new DocumentIngestionException("El markdown no puede quedar vacio antes de indexar.");
    }
    if (command.segments() == null || command.segments().isEmpty()) {
      throw new DocumentIngestionException("Necesito al menos un segmento para indexar.");
    }
    boolean hasBlankSegment =
        command.segments().stream()
            .anyMatch(segment -> segment.content() == null || segment.content().isBlank());
    if (hasBlankSegment) {
      throw new DocumentIngestionException(
          "Todos los segmentos deben tener contenido antes de indexar.");
    }
  }

  private List<DocumentSegmentEntity> deduplicateApprovedSegments(
      List<DocumentSegmentEntity> segments) {
    Map<String, DocumentSegmentEntity> uniqueByContent = new LinkedHashMap<>();
    for (DocumentSegmentEntity segment : segments) {
      uniqueByContent.putIfAbsent(normalize(segment.getContent()), segment);
    }
    return List.copyOf(uniqueByContent.values());
  }

  private boolean looksLikePdf(byte[] content) {
    return content.length >= 4
        && content[0] == '%'
        && content[1] == 'P'
        && content[2] == 'D'
        && content[3] == 'F';
  }

  private String normalize(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }

  private String sha256(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content);
      StringBuilder encoded = new StringBuilder(hash.length * 2);
      for (byte value : hash) {
        encoded.append(String.format("%02x", value));
      }
      return encoded.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 no esta disponible en la JVM.", exception);
    }
  }

  private void failIngestion(
      DocumentEntity document,
      DocumentIngestionJobEntity job,
      String message,
      Exception exception) {
    document.markFailed();
    documentPersistencePort.saveDocument(document);
    job.fail("La transformacion del PDF fallo.", message);
    documentPersistencePort.saveJob(job);
  }

  private void deleteQuietly(Path tempFile) {
    if (tempFile == null) {
      return;
    }
    try {
      Files.deleteIfExists(tempFile);
    } catch (IOException ignored) {
    }
  }

  private String safeMessage(Throwable throwable) {
    if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
      return "Ocurrio un error inesperado.";
    }
    return throwable.getMessage();
  }
}
