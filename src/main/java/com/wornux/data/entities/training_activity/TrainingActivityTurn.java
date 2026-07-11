package com.wornux.data.entities.training_activity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.wornux.services.training_activity.TutorDecisionType;
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
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "training_activity_turn")
@Getter
@Setter
public class TrainingActivityTurn {
    @Id private UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "training_activity_assignment_id", nullable = false)
    private TrainingActivityAssignment assignment;
    @Column(name = "sequence_number", nullable = false) private int sequenceNumber;
    @Column(name = "question_text", nullable = false) private String questionText;
    @Column(name = "question_created_at", nullable = false) private Instant questionCreatedAt;
    @Column(name = "answer_text") private String answerText;
    @Column(name = "answer_submission_id") private UUID answerSubmissionId;
    @Column(name = "answer_submitted_at") private Instant answerSubmittedAt;
    @Enumerated(EnumType.STRING) @Column(name = "decision_type") private TutorDecisionType decisionType;
    @Enumerated(EnumType.STRING) @Column(name = "answer_quality") private AnswerQuality answerQuality;
    @Enumerated(EnumType.STRING) @Column(name = "evidence_status") private EvidenceStatus evidenceStatus;
    @Enumerated(EnumType.STRING) @Column(name = "coverage_status") private CoverageStatus coverageStatus;
    @Enumerated(EnumType.STRING) @Column(name = "pedagogical_move") private PedagogicalMove pedagogicalMove;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "decision_metadata") private Map<String, Object> decisionMetadata;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
