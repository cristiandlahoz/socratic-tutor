package com.wornux.services.document;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.dtos.document.DocumentIngestionException;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.context.SetupRequiredException;
import com.wornux.ui.ingestion.EditableSegmentViewModel;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DocumentWorkspaceService {

    private static final String READY = "READY";
    private static final String INDEXED = "INDEXED";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbcClient;
    private final DocumentVectorIndexingService indexingService;
    private final ActiveAcademicContextResolver contextResolver;

    public DocumentWorkspaceService(
            JdbcClient jdbcClient,
            DocumentVectorIndexingService indexingService,
            ActiveAcademicContextResolver contextResolver) {
        this.jdbcClient = jdbcClient;
        this.indexingService = indexingService;
        this.contextResolver = contextResolver;
    }

    public List<DocumentWorkspaceCard> listDocuments() {
        var context = requireProfessorContext();
        return jdbcClient
                .sql("""
                     select metadata ->> 'ingestionId' as ingestion_id,
                            coalesce(max(metadata ->> 'title'), 'Documento sin título') as title,
                            count(*) as segment_count,
                            coalesce(max(metadata -> 'catalog' ->> 'label'), '') as catalog_label,
                            coalesce(max(metadata -> 'catalog' ->> 'useWhen'), '') as catalog_use_when
                     from grounding_vector_store
                     where metadata ->> 'groupClassId' = :groupClassId
                       and metadata ->> 'status' = :status
                       and nullif(metadata ->> 'ingestionId', '') is not null
                     group by metadata ->> 'ingestionId'
                     order by lower(coalesce(max(metadata ->> 'title'), ''))
                     """)
                .param("groupClassId", context.groupClassId().toString())
                .param("status", READY)
                .query(
                    (rs, rowNumber) -> new DocumentWorkspaceCard(
                            nonNullString(rs.getString("ingestion_id")),
                            nonNullString(rs.getString("title")),
                            INDEXED,
                            rs.getInt("segment_count"),
                            nonNullString(rs.getString("catalog_label")),
                            nonNullString(rs.getString("catalog_use_when"))))
                .list();
    }

    public DocumentWorkspaceDetail loadDocument(String ingestionId) {
        var context = requireProfessorContext();
        var parsedIngestionId = parseIngestionId(ingestionId);
        var rows = jdbcClient
                .sql("""
                     select id::text as vector_id,
                            content,
                            coalesce(metadata ->> 'title', 'Documento sin título') as title,
                            coalesce(metadata -> 'catalog' ->> 'label', '') as catalog_label,
                            coalesce(metadata -> 'catalog' ->> 'useWhen', '') as catalog_use_when,
                            coalesce(metadata -> 'catalog' -> 'aliases', '[]'::json) as catalog_aliases,
                            coalesce((metadata ->> 'chunkIndex')::int, 0) as chunk_index
                     from grounding_vector_store
                     where metadata ->> 'groupClassId' = :groupClassId
                       and metadata ->> 'ingestionId' = :ingestionId
                       and metadata ->> 'status' = :status
                     order by (metadata ->> 'chunkIndex')::int, id
                     """)
                .param("groupClassId", context.groupClassId().toString())
                .param("ingestionId", parsedIngestionId.toString())
                .param("status", READY)
                .query(
                    (rs, rowNumber) -> new DocumentVectorRow(
                            nonNullString(rs.getString("vector_id")),
                            nonNullString(rs.getString("content")),
                            nonNullString(rs.getString("title")),
                            nonNullString(rs.getString("catalog_label")),
                            nonNullString(rs.getString("catalog_use_when")),
                            nonNullString(rs.getString("catalog_aliases")),
                            rs.getInt("chunk_index")))
                .list();

        if (rows.isEmpty()) {
            throw new DocumentIngestionException("No se encontró el documento seleccionado.");
        }

        var first = rows.getFirst();
        var segments = rows.stream().map(this::toSegment).toList();
        var vectorIds = rows.stream().map(DocumentVectorRow::vectorId).toList();
        return new DocumentWorkspaceDetail(
            parsedIngestionId.toString(),
            first.title(),
            INDEXED,
            new CourseMaterialCatalog(first.catalogLabel(), first.catalogUseWhen(), parseAliases(first.catalogAliases())),
            rows.stream().map(DocumentVectorRow::content).reduce("", (left, right) -> left + "\n\n" + right).trim(),
            segments,
            vectorIds);
    }

    public DocumentWorkspaceDetail reindex(DocumentWorkspaceDetail detail) {
        var context = requireProfessorContext();
        return reindex(detail, context);
    }

    public DocumentWorkspaceDetail reindex(DocumentWorkspaceDetail detail, ActiveAcademicContext context) {
        requireProfessorContext(context);
        var ingestionId = parseIngestionId(detail.ingestionId());
        indexingService.delete(vectorIdsFor(context.groupClassId(), ingestionId));
        var segments = detail.segments() == null ? List.<EditableSegmentViewModel>of() : detail.segments();
        var nextSegments = normalizeOrdinals(segments);
        var catalog = detail.catalog() == null ? new CourseMaterialCatalog(detail.title(), "", List.of()) : detail.catalog();
        var vectorIds = indexingService.index(
            context.groupClassId(),
            context.groupClassMemberId(),
            ingestionId,
            detail.title(),
            catalog,
            nextSegments);
        return new DocumentWorkspaceDetail(
            ingestionId.toString(),
            detail.title(),
            INDEXED,
            catalog,
            detail.markdown(),
            nextSegments,
            vectorIds);
    }

    public void deleteDocument(String ingestionId) {
        var context = requireProfessorContext();
        var parsedIngestionId = parseIngestionId(ingestionId);
        var vectorIds = vectorIdsFor(context.groupClassId(), parsedIngestionId);
        indexingService.delete(vectorIds);
    }

    private List<String> vectorIdsFor(UUID groupClassId, UUID ingestionId) {
        return jdbcClient
                .sql("""
                     select id::text
                     from grounding_vector_store
                     where metadata ->> 'groupClassId' = :groupClassId
                       and metadata ->> 'ingestionId' = :ingestionId
                       and metadata ->> 'status' = :status
                     """)
                .param("groupClassId", groupClassId.toString())
                .param("ingestionId", ingestionId.toString())
                .param("status", READY)
                .query(String.class)
                .list();
    }

    private List<EditableSegmentViewModel> normalizeOrdinals(List<EditableSegmentViewModel> segments) {
        var sortedSegments = segments.stream()
                .filter(segment -> segment.content() != null && !segment.content().isBlank())
                .sorted(Comparator.comparingInt(EditableSegmentViewModel::ordinal))
                .toList();
        return java.util.stream.IntStream.range(0, sortedSegments.size())
                .mapToObj(index -> withOrdinal(sortedSegments.get(index), index + 1))
                .toList();
    }

    private EditableSegmentViewModel withOrdinal(EditableSegmentViewModel segment, int ordinal) {
        return new EditableSegmentViewModel(
            segment.id(),
            ordinal,
            segment.headingPath(),
            segment.content(),
            segment.approved(),
            segment.edited(),
            segment.content().length(),
            segment.tokenCount(),
            segment.pageNumber(),
            segment.pageNumbers(),
            segment.captions(),
            segment.docItems(),
            segment.rawText(),
            segment.chunker());
    }

    private EditableSegmentViewModel toSegment(DocumentVectorRow row) {
        return new EditableSegmentViewModel(
            row.vectorId(),
            row.chunkIndex(),
            "Documento",
            row.content(),
            true,
            false,
            row.content().length(),
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            row.content(),
            "vector-store");
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

    private UUID parseIngestionId(@Nullable String value) {
        if (value == null || value.isBlank()) {
            throw new DocumentIngestionException("El documento seleccionado no es válido.");
        }
        try {
            return UUID.fromString(value);
        }
        catch (RuntimeException exception) {
            throw new DocumentIngestionException("El documento seleccionado no es válido.", exception);
        }
    }

    private List<String> parseAliases(String value) {
        if (value == null || value.isBlank() || "[]".equals(value)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, STRING_LIST_TYPE);
        }
        catch (JsonProcessingException _) {
            return List.of();
        }
    }

    private String nonNullString(@Nullable String value) {
        return value == null ? "" : value;
    }

    private record DocumentVectorRow(
            String vectorId,
            String content,
            String title,
            String catalogLabel,
            String catalogUseWhen,
            String catalogAliases,
            int chunkIndex) {}
}
