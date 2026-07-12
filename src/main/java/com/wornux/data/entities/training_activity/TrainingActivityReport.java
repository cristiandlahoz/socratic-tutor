package com.wornux.data.entities.training_activity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "training_activity_report")
@Getter
@Setter
public class TrainingActivityReport {
    @Id private UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "training_activity_assignment_id", nullable = false)
    private TrainingActivityAssignment assignment;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TrainingActivityReportStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "evidence_status") private EvidenceStatus evidenceStatus;
    @Column private String summary;
    @JdbcTypeCode(SqlTypes.JSON) @Column private List<TrainingActivityReportFinding> strengths;
    @JdbcTypeCode(SqlTypes.JSON) @Column private List<TrainingActivityReportFinding> weaknesses;
    @JdbcTypeCode(SqlTypes.JSON) @Column private List<TrainingActivityReportFinding> observations;
    @JdbcTypeCode(SqlTypes.JSON) @Column private List<String> recommendations;
    @Column(name = "model_name", nullable = false) private String modelName;
    @Column(name = "prompt_version", nullable = false) private String promptVersion;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "last_error_code") private String lastErrorCode;
    @Version @Column(nullable = false) private long version;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
