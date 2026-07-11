package com.wornux.data.entities.training_activity;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.training_activity.instruction_review.TrainingInstructionReview;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "training_activity_ai_job")
@Getter
@Setter
public class TrainingActivityAiJob {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(name = "job_type", nullable = false) private TrainingActivityAiJobType jobType;
    @Column(nullable = false) private int priority;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "training_activity_id") private TrainingActivity trainingActivity;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "training_instruction_review_id") private TrainingInstructionReview instructionReview;
    @Column(name = "input_version", nullable = false) private long inputVersion;
    @Column(name = "semantic_key", nullable = false) private String semanticKey;
    @Column(nullable = false) private int generation;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TrainingActivityAiJobStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "max_attempts", nullable = false) private int maxAttempts;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "last_error_code") private String lastErrorCode;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public UUID getId() {
        return id;
    }
}
