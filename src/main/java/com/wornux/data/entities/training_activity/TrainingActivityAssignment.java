package com.wornux.data.entities.training_activity;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.training_activity.AnswerQuality;
import com.wornux.data.entities.training_activity.CoverageStatus;
import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.PedagogicalMove;
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
@Table(name = "training_activity_assignment")
@Getter
@Setter
public class TrainingActivityAssignment {

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "training_activity_id", nullable = false)
    private TrainingActivity trainingActivity;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "group_class_member_id", nullable = false)
    private GroupClassMember groupClassMember;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrainingActivityAssignmentStatus status;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "current_question")
    private String currentQuestion;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "evaluation_transcript", nullable = false)
    private String evaluationTranscript = "[]";

    @Column(name = "final_report")
    private String finalReport;

    @Column(name = "last_tutor_decision_json")
    private String lastTutorDecisionJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "tutor_answer_quality")
    private AnswerQuality tutorAnswerQuality;

    @Enumerated(EnumType.STRING)
    @Column(name = "tutor_evidence_status")
    private EvidenceStatus tutorEvidenceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "tutor_coverage_status")
    private CoverageStatus tutorCoverageStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "tutor_pedagogical_move")
    private PedagogicalMove tutorPedagogicalMove;

    @Column(name = "covered_instruction_aspects_json")
    private String coveredInstructionAspectsJson;

    @Column(name = "missing_instruction_aspects_json")
    private String missingInstructionAspectsJson;

    @Column(name = "unproductive_pattern_detected", nullable = false)
    private boolean unproductivePatternDetected;

    @Column(name = "insufficient_evidence", nullable = false)
    private boolean insufficientEvidence;

    @Column(name = "tutor_decision_reason")
    private String tutorDecisionReason;

    @Column(name = "tutor_model_name")
    private String tutorModelName;

    @Column(name = "tutor_prompt_version")
    private String tutorPromptVersion;

    @Column(name = "safe_browser_locked", nullable = false)
    private boolean safeBrowserLocked;

    @Column(name = "safe_browser_locked_at")
    private Instant safeBrowserLockedAt;

    @Column(name = "safe_browser_lock_reason")
    private String safeBrowserLockReason;

    @Column(name = "safe_browser_session_active", nullable = false)
    private boolean safeBrowserSessionActive;

    @Column(name = "safe_browser_last_heartbeat_at")
    private Instant safeBrowserLastHeartbeatAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
