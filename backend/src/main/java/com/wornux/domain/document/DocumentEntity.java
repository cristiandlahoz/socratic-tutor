package com.wornux.domain.document;

import com.wornux.ai.advisor.*;
import com.wornux.ai.config.*;
import com.wornux.ai.document.*;
import com.wornux.ai.guard.*;
import com.wornux.ai.memory.*;
import com.wornux.ai.profile.*;
import com.wornux.ai.prompt.*;
import com.wornux.ai.routing.*;
import com.wornux.ai.tools.*;
import com.wornux.application.chat.*;
import com.wornux.application.document.*;
import com.wornux.application.profile.*;
import com.wornux.domain.chat.*;
import com.wornux.domain.chat.questions.*;
import com.wornux.domain.profile.*;
import com.wornux.infrastructure.config.*;
import com.wornux.infrastructure.external.docling.*;
import com.wornux.infrastructure.persistence.chat.*;
import com.wornux.infrastructure.persistence.document.*;
import com.wornux.infrastructure.persistence.profile.*;
import com.wornux.infrastructure.web.*;
import com.wornux.presentation.chat.*;
import com.wornux.presentation.chat.ui.*;
import com.wornux.presentation.documentingest.*;
import com.wornux.presentation.documentingest.ui.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

  @Column(name = "catalog_title")
  private String catalogTitle;

  @Column(name = "catalog_topic")
  private String catalogTopic;

  @Column(name = "catalog_summary")
  private String catalogSummary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "catalog_tags", nullable = false, columnDefinition = "jsonb")
  private List<String> catalogTags = List.of();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "catalog_entities", nullable = false, columnDefinition = "jsonb")
  private List<String> catalogEntities = List.of();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "catalog_question_examples", nullable = false, columnDefinition = "jsonb")
  private List<String> catalogQuestionExamples = List.of();

  @Column(name = "catalog_stale", nullable = false)
  private boolean catalogStale;

  @Column(name = "catalog_updated_at")
  private Instant catalogUpdatedAt;

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

  public void applyCatalog(DocumentCatalogEntry catalog, boolean stale) {
    this.catalogTitle = catalog.title();
    this.catalogTopic = catalog.topic();
    this.catalogSummary = catalog.summary();
    this.catalogTags = catalog.tags() == null ? List.of() : List.copyOf(catalog.tags());
    this.catalogEntities = catalog.entities() == null ? List.of() : List.copyOf(catalog.entities());
    this.catalogQuestionExamples =
        catalog.questionExamples() == null ? List.of() : List.copyOf(catalog.questionExamples());
    this.catalogStale = stale;
    this.catalogUpdatedAt = Instant.now();
    touch();
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }
}
