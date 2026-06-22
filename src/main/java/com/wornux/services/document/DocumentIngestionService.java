package com.wornux.services.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.wornux.config.DocumentIngestionProperties;
import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.grounding.GroundingChunk;
import com.wornux.data.entities.grounding.GroundingCollection;
import com.wornux.data.entities.grounding.GroundingDocument;
import com.wornux.data.entities.grounding.GroundingDocumentSourceType;
import com.wornux.data.entities.grounding.GroundingDocumentStatus;
import com.wornux.data.enums.DocumentStatus;
import com.wornux.data.repositories.grounding.GroundingChunkRepository;
import com.wornux.data.repositories.grounding.GroundingCollectionRepository;
import com.wornux.data.repositories.grounding.GroundingDocumentRepository;
import com.wornux.dtos.document.DocumentIngestionException;
import com.wornux.infrastructure.external.docling.DoclingClientService;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.ui.ingestion.DocumentReviewViewModel;
import com.wornux.ui.ingestion.EditableSegmentViewModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentIngestionService {

    private static final String DEFAULT_COLLECTION_NAME = "Default grounding collection";

    private final GroundingDocumentRepository groundingDocumentRepository;
    private final GroundingChunkRepository groundingChunkRepository;
    private final GroundingCollectionRepository groundingCollectionRepository;
    private final DoclingClientService doclingClientService;
    private final DocumentEmbeddingService embeddingService;
    private final DocumentIngestionProperties properties;
    private final ActiveAcademicContextResolver contextResolver;

    public DocumentIngestionService(
            GroundingDocumentRepository groundingDocumentRepository,
            GroundingChunkRepository groundingChunkRepository,
            GroundingCollectionRepository groundingCollectionRepository,
            DoclingClientService doclingClientService,
            DocumentEmbeddingService embeddingService,
            DocumentIngestionProperties properties,
            ActiveAcademicContextResolver contextResolver) {
        this.groundingDocumentRepository = groundingDocumentRepository;
        this.groundingChunkRepository = groundingChunkRepository;
        this.groundingCollectionRepository = groundingCollectionRepository;
        this.doclingClientService = doclingClientService;
        this.embeddingService = embeddingService;
        this.properties = properties;
        this.contextResolver = contextResolver;
    }

    @Transactional
    public DocumentReviewViewModel startIngestion(StartIngestionCommand command) {
        validateUpload(command);
        var context = requireProfessorContext();

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("grounding-document-", ".pdf");
            Files.write(tempFile, command.content());

            var conversion = doclingClientService.convertPdfToMarkdownAndChunks(command.originalFilename(), command.content());
            if (conversion.markdown() == null || conversion.markdown().isBlank()) {
                throw new DocumentIngestionException("Docling returned an empty document.");
            }
            if (conversion.segments() == null || conversion.segments().isEmpty()) {
                throw new DocumentIngestionException("Docling could not create useful chunks for this PDF.");
            }

            var collection = findOrCreateCollection(context.groupClassId(), context.groupClassMemberId());
            var newDocument = new GroundingDocument();
            newDocument.setCollection(collection);
            newDocument.setTitle(command.originalFilename());
            newDocument.setSourceType(GroundingDocumentSourceType.UPLOAD);
            newDocument.setStatus(GroundingDocumentStatus.PROCESSING);
            newDocument.setCreatedAt(Instant.now());
            newDocument.setUpdatedAt(Instant.now());
            var document = groundingDocumentRepository.save(newDocument);

            groundingChunkRepository.deleteByDocument_Id(document.getId());
            List<GroundingChunk> chunks = new ArrayList<>();
            for (var segment : conversion.segments()) {
                var chunk = new GroundingChunk();
                chunk.setDocument(document);
                chunk.setChunkIndex(segment.ordinal());
                chunk.setContent(segment.content());
                chunk.setActive(true);
                chunk.setCreatedAt(Instant.now());
                chunks.add(chunk);
            }
            chunks = groundingChunkRepository.saveAll(chunks);
            return toReviewVm(document, conversion.markdown(), chunks, false, "Review the converted chunks before indexing.");
        }
        catch (IOException exception) {
            throw new DocumentIngestionException("Could not prepare the uploaded file.", exception);
        }
        finally {
            deleteQuietly(tempFile);
        }
    }

    @Transactional
    public DocumentReviewViewModel approve(ApproveDocumentCommand command) {
        var context = requireProfessorContext();
        var document = groundingDocumentRepository.findByIdAndCollection_GroupClass_Id(command.documentId(), context.groupClassId())
                .orElseThrow(() -> new DocumentIngestionException("Could not find that grounding document in the active class."));
        validateReview(command);

        var persistedChunks = groundingChunkRepository.findByDocument_IdOrderByChunkIndexAsc(document.getId());
        if (persistedChunks.size() != command.segments().size()) {
            groundingChunkRepository.deleteByDocument_Id(document.getId());
            persistedChunks = new ArrayList<>();
            for (int index = 0; index < command.segments().size(); index++) {
                var chunk = new GroundingChunk();
                chunk.setDocument(document);
                chunk.setChunkIndex(index);
                chunk.setActive(true);
                chunk.setCreatedAt(Instant.now());
                persistedChunks.add(chunk);
            }
            persistedChunks = groundingChunkRepository.saveAll(persistedChunks);
        }

        for (int index = 0; index < command.segments().size(); index++) {
            persistedChunks.get(index).setContent(command.segments().get(index).content());
        }
        persistedChunks = groundingChunkRepository.saveAll(persistedChunks);
        embeddingService.reindex(document, persistedChunks);

        document.setStatus(GroundingDocumentStatus.READY);
        document.setUpdatedAt(Instant.now());
        groundingDocumentRepository.save(document);
        return toReviewVm(document,
                command.reviewedMarkdown(),
                persistedChunks,
                true,
                "Grounding document indexed and ready for retrieval.");
    }

    @Transactional(readOnly = true)
    public java.util.Optional<DocumentReviewViewModel> loadLatestReview(UUID ignoredClientId) {
        return contextResolver.resolveCurrent()
                .flatMap(context -> groundingDocumentRepository
                        .findFirstByCollection_GroupClass_IdOrderByUpdatedAtDescCreatedAtDesc(context.groupClassId())
                        .map(document -> toReviewVm(document,
                                rebuildMarkdown(document.getId()),
                                groundingChunkRepository.findByDocument_IdOrderByChunkIndexAsc(document.getId()),
                                document.getStatus() == GroundingDocumentStatus.READY,
                                stageLabel(document))));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<DocumentReviewViewModel> loadReview(UUID ignoredClientId, Long documentId) {
        return contextResolver.resolveCurrent()
                .flatMap(context -> groundingDocumentRepository.findByIdAndCollection_GroupClass_Id(documentId, context.groupClassId())
                        .map(document -> toReviewVm(document,
                                rebuildMarkdown(document.getId()),
                                groundingChunkRepository.findByDocument_IdOrderByChunkIndexAsc(document.getId()),
                                document.getStatus() == GroundingDocumentStatus.READY,
                                stageLabel(document))));
    }

    @Transactional
    public void delete(UUID ignoredClientId, Long documentId) {
        var context = requireProfessorContext();
        var document = groundingDocumentRepository.findByIdAndCollection_GroupClass_Id(documentId, context.groupClassId())
                .orElseThrow(() -> new DocumentIngestionException("Could not find that grounding document in the active class."));
        var chunks = groundingChunkRepository.findByDocument_IdOrderByChunkIndexAsc(document.getId());
        embeddingService.deleteSegments(chunks);
        groundingChunkRepository.deleteByDocument_Id(document.getId());
        groundingDocumentRepository.delete(document);
    }

    private GroundingCollection findOrCreateCollection(UUID groupClassId, UUID groupClassMemberId) {
        return groundingCollectionRepository.findByGroupClass_IdOrderByCreatedAtDesc(groupClassId).stream().findFirst()
                .orElseGet(() -> {
                    var collection = new GroundingCollection();
                    collection.setGroupClass(new com.wornux.data.entities.academic.GroupClass());
                    collection.getGroupClass().setId(groupClassId);
                    collection.setCreatedByGroupClassMember(new com.wornux.data.entities.academic.GroupClassMember());
                    collection.getCreatedByGroupClassMember().setId(groupClassMemberId);
                    collection.setName(DEFAULT_COLLECTION_NAME);
                    collection.setActive(true);
                    collection.setCreatedAt(Instant.now());
                    collection.setUpdatedAt(Instant.now());
                    return groundingCollectionRepository.save(collection);
                });
    }

    private DocumentReviewViewModel toReviewVm(
            GroundingDocument document,
            String markdown,
            List<GroundingChunk> chunks,
            boolean indexed,
            String stageLabel) {
        return new DocumentReviewViewModel(document.getId(),
                document.getId(),
                document.getTitle(),
                indexed ? DocumentStatus.INDEXED : DocumentStatus.REVIEW_READY,
                stageLabel,
                markdown == null ? "" : markdown,
                chunks.stream().map(this::toSegmentVm).toList(),
                indexed);
    }

    private EditableSegmentViewModel toSegmentVm(GroundingChunk chunk) {
        return new EditableSegmentViewModel(chunk.getId(),
                chunk.getChunkIndex(),
                "Document",
                chunk.getContent(),
                true,
                false,
                chunk.getContent() == null ? 0 : chunk.getContent().length(),
                EditableSegmentViewModel.approximateTokens(chunk.getContent()),
                null,
                List.of(),
                List.of(),
                List.of(),
                chunk.getContent(),
                "grounding");
    }

    private String rebuildMarkdown(Long documentId) {
        return groundingChunkRepository.findByDocument_IdOrderByChunkIndexAsc(documentId).stream()
                .map(GroundingChunk::getContent)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String stageLabel(GroundingDocument document) {
        return switch (document.getStatus()) {
            case PROCESSING -> "Review the converted chunks before indexing.";
            case READY -> "Grounding document indexed and ready for retrieval.";
            case FAILED -> "Grounding document processing failed.";
            case INACTIVE -> "Grounding document is inactive.";
        };
    }

    private com.wornux.services.context.ActiveAcademicContext requireProfessorContext() {
        var context = contextResolver.requireCurrent();
        if (context.groupClassRole() != GroupClassMemberRole.PROFESSOR) {
            throw new SetupRequiredException("An active professor class context is required for grounding uploads.");
        }
        return context;
    }

    private void validateUpload(StartIngestionCommand command) {
        if (command.originalFilename() == null || command.originalFilename().isBlank()) {
            throw new DocumentIngestionException("The file needs a name.");
        }
        if (!command.originalFilename().toLowerCase().endsWith(".pdf")) {
            throw new DocumentIngestionException("Only PDF uploads are supported right now.");
        }
        if (command.content() == null || command.content().length == 0) {
            throw new DocumentIngestionException("The uploaded file is empty.");
        }
        if (command.content().length > properties.getMaxFileSizeBytes()) {
            throw new DocumentIngestionException("The PDF exceeds the configured size limit.");
        }
        if (!looksLikePdf(command.content())) {
            throw new DocumentIngestionException("The uploaded file does not look like a valid PDF.");
        }
    }

    private void validateReview(ApproveDocumentCommand command) {
        if (command.reviewedMarkdown() == null || command.reviewedMarkdown().isBlank()) {
            throw new DocumentIngestionException("Reviewed markdown cannot be empty before indexing.");
        }
        if (command.segments() == null || command.segments().isEmpty()) {
            throw new DocumentIngestionException("At least one chunk is required before indexing.");
        }
        boolean hasBlankSegment = command.segments().stream()
                .anyMatch(segment -> segment.content() == null || segment.content().isBlank());
        if (hasBlankSegment) {
            throw new DocumentIngestionException("Every chunk must contain text before indexing.");
        }
    }

    private boolean looksLikePdf(byte[] content) {
        return content.length >= 4 && content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
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
}
