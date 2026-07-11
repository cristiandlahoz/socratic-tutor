package com.wornux.data.entities.training_activity;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.identity.TenantAccount;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
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

@Entity(name = "TrainingActivity")
@Table(name = "training_activity")
@Getter
@Setter
public class TrainingActivity {

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "group_class_id", nullable = false)
    private GroupClass groupClass;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_tenant_account_id", nullable = false)
    private TenantAccount createdByTenantAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_group_class_member_id")
    private GroupClassMember createdByGroupClassMember;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrainingActivityLifecycleStatus status;

    @Column(name = "opens_at")
    private Instant opensAt;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "safe_browser_enabled", nullable = false)
    private boolean safeBrowserEnabled;

    @Column(name = "instruction_review_instructions_hash")
    private String instructionReviewInstructionsHash;

    @Column(name = "instruction_review_hash")
    private String instructionReviewHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "instruction_review_status")
    private InstructionReviewStatus instructionReviewStatus;

    @Column(name = "instruction_review_message")
    private String instructionReviewMessage;

    @Column(name = "instruction_review_valid_instruction")
    private Boolean instructionReviewValidInstruction;

    @Enumerated(EnumType.STRING)
    @Column(name = "instruction_review_quality_status")
    private InstructionQualityStatus instructionReviewQualityStatus;

    @Column(name = "instruction_review_summary")
    private String instructionReviewSummary;

    @Column(name = "instruction_review_issues_json")
    private String instructionReviewIssuesJson;

    @Column(name = "instruction_review_improved_instructions")
    private String instructionReviewImprovedInstructions;

    @Column(name = "instruction_review_model_name")
    private String instructionReviewModelName;

    @Column(name = "instruction_review_rubric_version")
    private String instructionReviewRubricVersion;

    @Column(name = "instruction_review_prompt_version")
    private String instructionReviewPromptVersion;

    @Column(name = "instruction_reviewed_at")
    private Instant instructionReviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
