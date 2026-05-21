package com.wornux.domain.document;

import com.wornux.presentation.documentingest.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "document_segment")
public class DocumentSegmentEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(nullable = false)
    private Integer ordinal;

    @Column(name = "heading_path")
    private String headingPath;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private boolean approved;

    @Column(nullable = false)
    private boolean edited;

    @Column(name = "char_count", nullable = false)
    private Integer charCount;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;

    @Column(name = "page_number")
    private Integer pageNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_page_numbers", nullable = false, columnDefinition = "jsonb")
    private List<Integer> sourcePageNumbers = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> captions = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "doc_items", nullable = false, columnDefinition = "jsonb")
    private List<String> docItems = List.of();

    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @Column(nullable = false)
    private String chunker;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static DocumentSegmentEntity createDraft(
            DocumentEntity document,
            int ordinal,
            String headingPath,
            String content,
            Integer tokenCount,
            Integer pageNumber,
            List<Integer> sourcePageNumbers,
            List<String> captions,
            List<String> docItems,
            String rawText) {
        var entity = new DocumentSegmentEntity();
        entity.id = UUID.randomUUID();
        entity.document = document;
        entity.ordinal = ordinal;
        entity.headingPath = headingPath;
        entity.content = content;
        entity.approved = false;
        entity.edited = false;
        entity.charCount = content.length();
        entity.tokenCount = tokenCount == null ? EditableSegmentVm.approximateTokens(content) : tokenCount;
        entity.pageNumber = pageNumber;
        entity.sourcePageNumbers = safeIntegerList(sourcePageNumbers);
        entity.captions = safeStringList(captions);
        entity.docItems = safeStringList(docItems);
        entity.rawText = rawText;
        entity.chunker = "DOCLING_HYBRID";
        entity.createdAt = Instant.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void applyReview(EditableSegmentVm segment) {
        this.content = segment.content();
        this.headingPath = segment.headingPath();
        this.approved = true;
        this.edited = segment.edited();
        this.charCount = segment.charCount() == null ? segment.content().length() : segment.charCount();
        this.tokenCount = segment.tokenCount() == null
                ? EditableSegmentVm.approximateTokens(segment.content())
                : segment.tokenCount();
        this.pageNumber = segment.pageNumber();
        this.sourcePageNumbers = safeIntegerList(segment.pageNumbers());
        this.captions = safeStringList(segment.captions());
        this.docItems = safeStringList(segment.docItems());
        this.rawText = segment.rawText();
        this.chunker = segment.chunker() == null || segment.chunker().isBlank() ? chunker : segment.chunker();
        this.updatedAt = Instant.now();
    }

    public EditableSegmentVm toViewModel() {
        return new EditableSegmentVm(id,
                ordinal,
                headingPath,
                content,
                approved,
                edited,
                charCount,
                tokenCount,
                pageNumber,
                sourcePageNumbers,
                captions,
                docItems,
                rawText,
                chunker);
    }

    private static List<String> safeStringList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static List<Integer> safeIntegerList(List<Integer> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
