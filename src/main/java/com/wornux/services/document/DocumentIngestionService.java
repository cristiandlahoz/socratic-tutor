package com.wornux.services.document;

import java.util.List;
import java.util.UUID;

import com.wornux.config.DocumentIngestionProperties;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.enums.DocumentStatus;
import com.wornux.dtos.document.DoclingSegmentDraft;
import com.wornux.dtos.document.DocumentIngestionException;
import com.wornux.infrastructure.external.docling.DoclingClientService;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.ui.ingestion.DocumentReviewViewModel;
import com.wornux.ui.ingestion.EditableSegmentViewModel;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionService {

    private final DoclingClientService doclingClientService;
    private final DocumentVectorIndexingService indexingService;
    private final DocumentCatalogGenerationService catalogGenerationService;
    private final DocumentIngestionProperties properties;
    private final ActiveAcademicContextResolver contextResolver;

    public DocumentIngestionService(
            DoclingClientService doclingClientService,
            DocumentVectorIndexingService indexingService,
            DocumentCatalogGenerationService catalogGenerationService,
            DocumentIngestionProperties properties,
            ActiveAcademicContextResolver contextResolver) {
        this.doclingClientService = doclingClientService;
        this.indexingService = indexingService;
        this.catalogGenerationService = catalogGenerationService;
        this.properties = properties;
        this.contextResolver = contextResolver;
    }

    public DocumentReviewViewModel startIngestion(StartIngestionCommand command) {
        validateUpload(command);
        requireProfessorContext();

        return startIngestionAfterValidation(command);
    }

    public DocumentReviewViewModel startIngestion(StartIngestionCommand command, ActiveAcademicContext context) {
        validateUpload(command);
        requireProfessorContext(context);

        return startIngestionAfterValidation(command);
    }

    private DocumentReviewViewModel startIngestionAfterValidation(StartIngestionCommand command) {

        var conversion =
                doclingClientService.convertPdfToMarkdownAndChunks(command.originalFilename(), command.content());
        if (conversion.markdown() == null || conversion.markdown().isBlank()) {
            throw new DocumentIngestionException("Docling returned an empty document.");
        }
        if (conversion.segments() == null || conversion.segments().isEmpty()) {
            throw new DocumentIngestionException("Docling could not create useful chunks for this PDF.");
        }

        return new DocumentReviewViewModel(UUID.randomUUID().toString(),
                command.originalFilename(),
                DocumentStatus.REVIEW_READY,
                "Review the converted chunks before indexing.",
                conversion.markdown(),
                conversion.segments().stream().map(this::toSegmentVm).toList(),
                false,
                List.of());
    }

    public DocumentReviewViewModel approve(ApproveDocumentCommand command) {
        var context = requireProfessorContext();
        return approve(command, context);
    }

    public DocumentReviewViewModel approve(ApproveDocumentCommand command, ActiveAcademicContext context) {
        requireProfessorContext(context);
        validateReview(command);
        UUID ingestionId = parseIngestionId(command.ingestionId());
        List<String> vectorIds = indexingService.index(
            context.groupClassId(),
            context.groupClassMemberId(),
            ingestionId,
            command.title(),
            command.catalog(),
            command.segments());

        return new DocumentReviewViewModel(ingestionId.toString(),
                command.title(),
                DocumentStatus.INDEXED,
                "Grounding document indexed and ready for retrieval.",
                command.reviewedMarkdown(),
                command.segments(),
                true,
                vectorIds);
    }

    public CourseMaterialCatalog generateCatalog(
            String title,
            String catalogUseWhen,
            List<EditableSegmentViewModel> segments) {
        requireProfessorContext();
        return catalogGenerationService.generate(title, catalogUseWhen, segments);
    }

    public CourseMaterialCatalog generateCatalog(
            String title,
            String catalogUseWhen,
            List<EditableSegmentViewModel> segments,
            ActiveAcademicContext context) {
        requireProfessorContext(context);
        return catalogGenerationService.generate(title, catalogUseWhen, segments);
    }

    public void delete(List<String> vectorIds) {
        requireProfessorContext();
        indexingService.delete(vectorIds);
    }

    private EditableSegmentViewModel toSegmentVm(DoclingSegmentDraft segment) {
        return new EditableSegmentViewModel(UUID.randomUUID().toString(),
                segment.ordinal(),
                segment.headingPath(),
                segment.content(),
                true,
                false,
                segment.content() == null ? 0 : segment.content().length(),
                segment.tokenCount(),
                segment.pageNumber(),
                segment.pageNumbers() == null ? List.of() : segment.pageNumbers(),
                segment.captions() == null ? List.of() : segment.captions(),
                segment.docItems() == null ? List.of() : segment.docItems(),
                segment.rawText(),
                "docling");
    }

    private ActiveAcademicContext requireProfessorContext() {
        var context = contextResolver.requireCurrent();
        requireProfessorContext(context);
        return context;
    }

    private void requireProfessorContext(ActiveAcademicContext context) {
        if (context.groupClassKind() != GroupClassMemberKind.PROFESSOR) {
            throw new SetupRequiredException("An active professor class context is required for grounding uploads.");
        }
    }

    private UUID parseIngestionId(String value) {
        try {
            return UUID.fromString(value);
        }
        catch (RuntimeException exception) {
            throw new DocumentIngestionException("The active ingestion session is invalid.", exception);
        }
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
        requireTitle(command);
        requireCatalogUseWhen(command);
        requireReviewedMarkdown(command);
        requireSegments(command);
    }

    private void requireTitle(ApproveDocumentCommand command) {
        if (command.title() == null || command.title().isBlank()) {
            throw new DocumentIngestionException("The document title cannot be empty before indexing.");
        }
    }

    private void requireCatalogUseWhen(ApproveDocumentCommand command) {
        if (command.catalog() == null) {
            throw new DocumentIngestionException("Generate and accept the catalog before indexing.");
        }
    }

    private void requireReviewedMarkdown(ApproveDocumentCommand command) {
        if (command.reviewedMarkdown() == null || command.reviewedMarkdown().isBlank()) {
            throw new DocumentIngestionException("Reviewed markdown cannot be empty before indexing.");
        }
    }

    private void requireSegments(ApproveDocumentCommand command) {
        if (command.segments() == null || command.segments().isEmpty()) {
            throw new DocumentIngestionException("At least one chunk is required before indexing.");
        }
        if (command.segments().stream().anyMatch(this::isBlankSegment)) {
            throw new DocumentIngestionException("Every chunk must contain text before indexing.");
        }
    }

    private boolean isBlankSegment(EditableSegmentViewModel segment) {
        return segment.content() == null || segment.content().isBlank();
    }

    private boolean looksLikePdf(byte[] content) {
        return content.length >= 4 && content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
    }
}
