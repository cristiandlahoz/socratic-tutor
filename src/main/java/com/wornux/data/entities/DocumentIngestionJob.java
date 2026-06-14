package com.wornux.data.entities;

import java.time.Instant;

import com.wornux.data.enums.DocumentIngestionStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "document_ingestion_job")
public class DocumentIngestionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false, length = 24)
    private String stage;

    @Column(name = "progress_label", nullable = false)
    private String progressLabel;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static DocumentIngestionJob start(Document document, String progressLabel) {
        var job = new DocumentIngestionJob();
        job.document = document;
        job.stage = DocumentIngestionStage.UPLOAD.name();
        job.progressLabel = progressLabel;
        job.startedAt = Instant.now();
        return job;
    }

    public DocumentIngestionStage stage() {
        return DocumentIngestionStage.valueOf(stage);
    }

    public void advance(DocumentIngestionStage nextStage, String nextLabel) {
        this.stage = nextStage.name();
        this.progressLabel = nextLabel;
        this.failureMessage = null;
    }

    public void complete(String nextLabel) {
        this.stage = DocumentIngestionStage.COMPLETE.name();
        this.progressLabel = nextLabel;
        this.failureMessage = null;
        this.completedAt = Instant.now();
    }

    public void fail(String nextLabel, String error) {
        this.stage = DocumentIngestionStage.FAILED.name();
        this.progressLabel = nextLabel;
        this.failureMessage = error;
        this.completedAt = Instant.now();
    }
}
