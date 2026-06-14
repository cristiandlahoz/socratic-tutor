package com.wornux.services.document;

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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wornux.config.DocumentIngestionProperties;
import com.wornux.data.entities.Document;
import com.wornux.data.entities.DocumentIngestionJob;
import com.wornux.data.entities.DocumentSegment;
import com.wornux.data.enums.DocumentIngestionStage;
import com.wornux.data.enums.DocumentStatus;
import com.wornux.data.repositories.document.DocumentIngestionJobRepository;
import com.wornux.data.repositories.document.DocumentRepository;
import com.wornux.data.repositories.document.DocumentSegmentRepository;
import com.wornux.dtos.document.DocumentIngestionException;
import com.wornux.infrastructure.external.docling.DoclingClientService;
import com.wornux.ui.ingestion.DocumentReviewViewModel;
import com.wornux.ui.ingestion.EditableSegmentViewModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final DocumentSegmentRepository segmentRepository;
    private final DocumentIngestionJobRepository ingestionRepository;
    private final DoclingClientService doclingClientService;
    private final DocumentCatalogService catalogService;
    private final DocumentEmbeddingService embeddingService;
    private final DocumentIngestionProperties properties;

    public DocumentIngestionService(
            DocumentRepository documentRepository,
            DocumentSegmentRepository segmentRepository,
            DocumentIngestionJobRepository jobRepository,
            DoclingClientService doclingClientService,
            DocumentCatalogService catalogService,
            DocumentEmbeddingService embeddingService,
            DocumentIngestionProperties properties) {
        this.documentRepository = documentRepository;
        this.segmentRepository = segmentRepository;
        this.ingestionRepository = jobRepository;
        this.doclingClientService = doclingClientService;
        this.catalogService = catalogService;
        this.embeddingService = embeddingService;
        this.properties = properties;
    }

    public DocumentReviewViewModel startIngestion(StartIngestionCommand command) {
        validateUpload(command);

        var document = documentRepository.save(Document.create(command, sha256(command.content())));
        var job = ingestionRepository
                .save(DocumentIngestionJob.start(document, "PDF recibido. Preparando transformacion."));

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("ingested-document-", ".pdf");
            Files.write(tempFile, command.content());

            job.advance(DocumentIngestionStage.DOCLING_CONVERT, "Transformando y segmentando PDF con Docling.");
            ingestionRepository.save(job);

            var conversion =
                    doclingClientService.convertPdfToMarkdownAndChunks(command.originalFilename(), command.content());
            if (conversion.markdown() == null || conversion.markdown().isBlank()) {
                throw new DocumentIngestionException("Docling devolvio un documento vacio.");
            }
            if (conversion.segments() == null || conversion.segments().isEmpty()) {
                throw new DocumentIngestionException("Docling no pudo crear segmentos utiles para este PDF.");
            }

            document.markReviewReady(conversion.markdown(), conversion.pageCount());
            var initialCatalog = catalogService.analyzeOrFallback(command.originalFilename(), conversion.markdown());
            document.applyCatalog(initialCatalog.entry(), initialCatalog.stale());
            documentRepository.save(document);

            job.advance(DocumentIngestionStage.SEGMENT_BUILD, "Preparando segmentos de Docling.");
            ingestionRepository.save(job);

            segmentRepository.deleteByDocument_Id(document.getId());
            segmentRepository.saveAll(
                conversion.segments()
                        .stream()
                        .map(
                            segment -> DocumentSegment.createDraft(
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

            job.advance(DocumentIngestionStage.REVIEW, "Revisa el markdown y valida los segmentos antes de indexar.");
            ingestionRepository.save(job);

            return toReviewVm(document, job);
        }
        catch (IOException exception) {
            failIngestion(document, job, "No se pudo preparar el archivo temporal del PDF.", exception);
            throw new DocumentIngestionException("No se pudo procesar el archivo subido.", exception);
        }
        catch (RuntimeException exception) {
            failIngestion(document, job, safeMessage(exception), exception);
            throw exception;
        }
        finally {
            deleteQuietly(tempFile);
        }
    }

    public DocumentReviewViewModel approve(ApproveDocumentCommand command) {
        var document = documentRepository.findByIdAndClientId(command.documentId(), command.clientId())
                .orElseThrow(() -> new DocumentIngestionException("No encontre ese documento para este usuario."));
        var job = ingestionRepository.findFirstByDocument_IdOrderByStartedAtDesc(document.getId())
                .orElseThrow(() -> new DocumentIngestionException("No encontre el job de ingestion del documento."));

        if (document.status() == DocumentStatus.INDEXED) {
            return toReviewVm(document, job);
        }

        validateReview(command);

        try {
            job.advance(DocumentIngestionStage.EMBED, "Indexando segmentos para que el chat pueda buscarlos.");
            ingestionRepository.save(job);

            document.markApproved(command.reviewedMarkdown());
            var refreshedCatalog =
                    catalogService.analyzeOrFallback(document.getOriginalFilename(), command.reviewedMarkdown());
            document.applyCatalog(refreshedCatalog.entry(), refreshedCatalog.stale());
            documentRepository.save(document);

            var persistedSegments = segmentRepository.findByDocument_IdOrderByOrdinalAsc(document.getId())
                    .stream()
                    .collect(
                        Collectors.toMap(
                            DocumentSegment::getId,
                            segment -> segment,
                            (left, _) -> left,
                            LinkedHashMap::new));

            Set<Long> reviewedSegmentIds =
                    command.segments().stream().map(EditableSegmentViewModel::id).collect(Collectors.toSet());
            List<DocumentSegment> removedSegments = persistedSegments.values()
                    .stream()
                    .filter(segment -> !reviewedSegmentIds.contains(segment.getId()))
                    .toList();
            List<DocumentSegment> approvedSegments = new ArrayList<>();

            for (EditableSegmentViewModel reviewedSegment : command.segments()) {
                var segment = persistedSegments.get(reviewedSegment.id());
                if (segment == null) {
                    throw new DocumentIngestionException("La revision contiene un segmento desconocido.");
                }
                segment.applyReview(reviewedSegment);
                approvedSegments.add(segment);
            }
            segmentRepository.deleteAll(removedSegments);
            segmentRepository.saveAll(approvedSegments);

            List<DocumentSegment> uniqueSegments = deduplicateApprovedSegments(approvedSegments);
            embeddingService.reindex(document, uniqueSegments);

            document.markIndexed(command.reviewedMarkdown());
            documentRepository.save(document);
            job.complete("Documento indexado. Ya puedes preguntarle al chat.");
            ingestionRepository.save(job);

            return toReviewVm(document, job);
        }
        catch (RuntimeException exception) {
            embeddingService.deleteSegments(segmentRepository.findByDocument_IdOrderByOrdinalAsc(document.getId()));
            document.markFailed();
            documentRepository.save(document);
            job.fail("La indexacion fallo.", safeMessage(exception));
            ingestionRepository.save(job);
            throw exception;
        }
    }

    public Optional<DocumentReviewViewModel> loadLatestReview(UUID clientId) {
        return documentRepository.findFirstByClientIdOrderByUpdatedAtDesc(clientId)
                .flatMap(
                    document -> ingestionRepository.findFirstByDocument_IdOrderByStartedAtDesc(document.getId())
                            .map(job -> toReviewVm(document, job)));
    }

    public Optional<DocumentReviewViewModel> loadReview(UUID clientId, UUID documentId) {
        return documentRepository.findByIdAndClientId(documentId, clientId)
                .flatMap(
                    document -> ingestionRepository.findFirstByDocument_IdOrderByStartedAtDesc(document.getId())
                            .map(job -> toReviewVm(document, job)));
    }

    @Transactional
    public void delete(UUID clientId, UUID documentId) {
        var document = documentRepository.findByIdAndClientId(documentId, clientId)
                .orElseThrow(() -> new DocumentIngestionException("No encontre ese documento para este usuario."));
        var segments = segmentRepository.findByDocument_IdOrderByOrdinalAsc(document.getId());
        embeddingService.deleteSegments(segments);
        documentRepository.delete(document);
    }

    private DocumentReviewViewModel toReviewVm(Document document, DocumentIngestionJob job) {
        return new DocumentReviewViewModel(document.getId(),
                job.getId(),
                document.getOriginalFilename(),
                document.status(),
                job.getProgressLabel(),
                document.getReviewedMarkdown() == null ? "" : document.getReviewedMarkdown(),
                segmentRepository.findByDocument_IdOrderByOrdinalAsc(document.getId())
                        .stream()
                        .map(DocumentSegment::toViewModel)
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
        boolean hasBlankSegment = command.segments()
                .stream()
                .anyMatch(segment -> segment.content() == null || segment.content().isBlank());
        if (hasBlankSegment) {
            throw new DocumentIngestionException("Todos los segmentos deben tener contenido antes de indexar.");
        }
    }

    private List<DocumentSegment> deduplicateApprovedSegments(List<DocumentSegment> segments) {
        Map<String, DocumentSegment> uniqueByContent = new LinkedHashMap<>();
        for (DocumentSegment segment : segments) {
            uniqueByContent.putIfAbsent(normalize(segment.getContent()), segment);
        }
        return List.copyOf(uniqueByContent.values());
    }

    private boolean looksLikePdf(byte[] content) {
        return content.length >= 4 && content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
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
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no esta disponible en la JVM.", exception);
        }
    }

    private void failIngestion(Document document, DocumentIngestionJob job, String message, Exception exception) {
        document.markFailed();
        documentRepository.save(document);
        job.fail("La transformacion del PDF fallo.", message);
        ingestionRepository.save(job);
    }

    private void deleteQuietly(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        }
        catch (IOException ignored) {}
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "Ocurrio un error inesperado.";
        }
        return throwable.getMessage();
    }
}
