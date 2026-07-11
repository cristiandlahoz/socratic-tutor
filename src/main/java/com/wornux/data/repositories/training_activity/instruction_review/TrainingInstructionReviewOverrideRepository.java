package com.wornux.data.repositories.training_activity.instruction_review;

import java.util.UUID;

import com.wornux.data.entities.training_activity.instruction_review.TrainingInstructionReviewOverride;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingInstructionReviewOverrideRepository extends JpaRepository<TrainingInstructionReviewOverride, UUID> {
}
