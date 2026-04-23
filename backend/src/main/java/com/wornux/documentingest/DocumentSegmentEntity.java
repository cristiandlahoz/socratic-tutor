package com.wornux.documentingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "document_segment")
public class DocumentSegmentEntity {

  @Id private UUID id;

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

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static DocumentSegmentEntity createDraft(
      DocumentEntity document, int ordinal, String headingPath, String content) {
    var entity = new DocumentSegmentEntity();
    entity.id = UUID.randomUUID();
    entity.document = document;
    entity.ordinal = ordinal;
    entity.headingPath = headingPath;
    entity.content = content;
    entity.approved = false;
    entity.edited = false;
    entity.charCount = content.length();
    entity.tokenCount = EditableSegmentVm.approximateTokens(content);
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
    this.tokenCount =
        segment.tokenCount() == null
            ? EditableSegmentVm.approximateTokens(segment.content())
            : segment.tokenCount();
    this.pageNumber = segment.pageNumber();
    this.updatedAt = Instant.now();
  }

  public EditableSegmentVm toViewModel() {
    return new EditableSegmentVm(
        id, ordinal, headingPath, content, approved, edited, charCount, tokenCount, pageNumber);
  }
}
