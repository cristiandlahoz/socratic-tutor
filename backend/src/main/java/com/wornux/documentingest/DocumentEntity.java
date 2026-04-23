package com.wornux.documentingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "ingested_document")
public class DocumentEntity {

  @Id private UUID id;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Column(name = "original_filename", nullable = false)
  private String originalFilename;

  @Column(name = "mime_type", nullable = false)
  private String mimeType;

  @Column(name = "source_type", nullable = false)
  private String sourceType;

  @Column(name = "docling_format", nullable = false)
  private String doclingFormat;

  @Column(name = "checksum_sha256", nullable = false, length = 64)
  private String checksumSha256;

  @Column(nullable = false, length = 24)
  private String status;

  @Column(name = "reviewed_markdown", columnDefinition = "text")
  private String reviewedMarkdown;

  @Column(name = "page_count")
  private Integer pageCount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static DocumentEntity create(StartIngestionCommand command, String checksumSha256) {
    var now = Instant.now();
    var entity = new DocumentEntity();
    entity.id = UUID.randomUUID();
    entity.clientId = command.clientId();
    entity.originalFilename = command.originalFilename();
    entity.mimeType = command.mimeType();
    entity.sourceType = "browser_upload";
    entity.doclingFormat = "markdown";
    entity.checksumSha256 = checksumSha256;
    entity.status = DocumentStatus.UPLOADED.name();
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public DocumentStatus status() {
    return DocumentStatus.valueOf(status);
  }

  public void markReviewReady(String markdown, Integer pageCount) {
    this.reviewedMarkdown = markdown;
    this.pageCount = pageCount;
    this.status = DocumentStatus.REVIEW_READY.name();
    touch();
  }

  public void markApproved(String markdown) {
    this.reviewedMarkdown = markdown;
    this.status = DocumentStatus.APPROVED.name();
    touch();
  }

  public void markIndexed(String markdown) {
    this.reviewedMarkdown = markdown;
    this.status = DocumentStatus.INDEXED.name();
    touch();
  }

  public void markFailed() {
    this.status = DocumentStatus.FAILED.name();
    touch();
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }
}
