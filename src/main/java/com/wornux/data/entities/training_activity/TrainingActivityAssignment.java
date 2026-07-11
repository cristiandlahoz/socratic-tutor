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
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
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

    @Version
    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_status")
    private EvidenceStatus evidenceStatus;

    @Column(name = "completion_reason")
    private String completionReason;

    // Non-authoritative compatibility projection for legacy UI/report components.
    // UC-007 persists the transcript exclusively in training_activity_turn.
    @Transient private String currentQuestion;
    @Transient private int questionCount;
    @Transient private String evaluationTranscript = "[]";
    @Transient private String finalReport;
    @Transient private String lastTutorDecisionJson;
    @Transient private AnswerQuality tutorAnswerQuality;
    @Transient private EvidenceStatus tutorEvidenceStatus;
    @Transient private CoverageStatus tutorCoverageStatus;
    @Transient private PedagogicalMove tutorPedagogicalMove;
    @Transient private String coveredInstructionAspectsJson;
    @Transient private String missingInstructionAspectsJson;
    @Transient private boolean unproductivePatternDetected;
    @Transient private boolean insufficientEvidence;
    @Transient private String tutorDecisionReason;
    @Transient private String tutorModelName;
    @Transient private String tutorPromptVersion;

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
