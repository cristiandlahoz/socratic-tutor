package com.wornux.data.entities.training_activity.instruction_review;

import java.time.Instant;

import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "InstructionReviewCacheEntry")
@Table(name = "instruction_review_cache")
@Getter
@Setter
public class InstructionReviewCacheEntry {

    @Id
    @Column(name = "review_hash", nullable = false)
    private String reviewHash;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "normalized_title_hash", nullable = false)
    private String normalizedTitleHash;

    @Column(name = "normalized_instructions_hash", nullable = false)
    private String normalizedInstructionsHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private InstructionReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status")
    private InstructionQualityStatus qualityStatus;

    @Column(name = "valid_instruction")
    private Boolean validInstruction;

    @Column(name = "issues_json")
    private String issuesJson;

    @Column(name = "review_message")
    private String reviewMessage;

    @Column(name = "recreated_instructions")
    private String recreatedInstructions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
