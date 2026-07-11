package com.wornux.data.entities.training_activity.instruction_review;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.training_activity.TrainingActivity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "training_instruction_review_override")
@Getter
@Setter
public class TrainingInstructionReviewOverride {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "training_activity_id") private TrainingActivity trainingActivity;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "training_instruction_review_id") private TrainingInstructionReview trainingInstructionReview;
    @Column(name = "instructions_hash", nullable = false) private String instructionsHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private InstructionReviewOverrideAction action;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_group_class_member_id", nullable = false) private GroupClassMember actorGroupClassMember;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
