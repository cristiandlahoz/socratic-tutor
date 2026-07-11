package com.wornux.data.entities.training_activity.instruction_review;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.training_activity.TrainingActivity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "training_instruction_review")
@Getter
@Setter
public class TrainingInstructionReview {
    @Id private UUID id;
    @Column(name = "candidate_id", nullable = false) private UUID candidateId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "training_activity_id") private TrainingActivity trainingActivity;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "group_class_id", nullable = false) private GroupClass groupClass;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "requested_by_group_class_member_id", nullable = false) private GroupClassMember requestedByGroupClassMember;
    @Column(name = "title_snapshot", nullable = false) private String titleSnapshot;
    @Column(name = "instructions_snapshot", nullable = false) private String instructionsSnapshot;
    @Column(name = "instructions_hash", nullable = false) private String instructionsHash;
    @Enumerated(EnumType.STRING) @Column(name = "execution_status", nullable = false) private TrainingInstructionReviewExecutionStatus executionStatus;
    @Enumerated(EnumType.STRING) @Column(name = "outcome") private InstructionReviewOutcome outcome;
    private String summary;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "issues_json", columnDefinition = "jsonb") private String issuesJson;
    @Column(name = "improved_instructions") private String improvedInstructions;
    @Column(name = "model_name", nullable = false) private String modelName;
    @Column(name = "rubric_version", nullable = false) private String rubricVersion;
    @Column(name = "failure_code") private String failureCode;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "completed_at") private Instant completedAt;
}
