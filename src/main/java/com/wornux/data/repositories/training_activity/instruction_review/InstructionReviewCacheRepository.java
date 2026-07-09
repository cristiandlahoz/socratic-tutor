package com.wornux.data.repositories.training_activity.instruction_review;

import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstructionReviewCacheRepository extends JpaRepository<InstructionReviewCacheEntry, String> {
}
