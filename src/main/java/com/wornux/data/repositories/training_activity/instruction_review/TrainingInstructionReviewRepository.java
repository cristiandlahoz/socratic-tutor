package com.wornux.data.repositories.training_activity.instruction_review;

import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.training_activity.instruction_review.TrainingInstructionReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingInstructionReviewRepository extends JpaRepository<TrainingInstructionReview, UUID> {
    Optional<TrainingInstructionReview> findByCandidateIdAndGroupClass_IdAndRequestedByGroupClassMember_IdAndInstructionsHashAndModelNameAndRubricVersion(
            UUID candidateId, UUID groupClassId, UUID actorMemberId, String instructionsHash, String modelName, String rubricVersion);
    Optional<TrainingInstructionReview> findFirstByTrainingActivity_IdAndGroupClass_IdAndRequestedByGroupClassMember_IdAndInstructionsHashAndModelNameAndRubricVersionOrderByRequestedAtDesc(
            UUID trainingActivityId, UUID groupClassId, UUID actorMemberId, String instructionsHash, String modelName, String rubricVersion);
    Optional<TrainingInstructionReview> findFirstByTrainingActivity_IdOrderByRequestedAtDesc(UUID trainingActivityId);
    Optional<TrainingInstructionReview> findFirstByTrainingActivity_IdAndInstructionsHashAndModelNameAndRubricVersionOrderByRequestedAtDesc(
            UUID trainingActivityId, String instructionsHash, String modelName, String rubricVersion);

    @Modifying
    @Query(value = """
            insert into training_instruction_review (
                id, candidate_id, training_activity_id, group_class_id, requested_by_group_class_member_id,
                title_snapshot, instructions_snapshot, instructions_hash, execution_status,
                model_name, rubric_version, requested_at)
            values (
                :id, :candidateId, :activityId, :groupClassId, :actorMemberId,
                :title, :instructions, :instructionsHash, 'PENDING',
                :modelName, :rubricVersion, :requestedAt)
            on conflict on constraint uk_training_instruction_review_semantic do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("candidateId") UUID candidateId,
            @Param("activityId") UUID activityId,
            @Param("groupClassId") UUID groupClassId,
            @Param("actorMemberId") UUID actorMemberId,
            @Param("title") String title,
            @Param("instructions") String instructions,
            @Param("instructionsHash") String instructionsHash,
            @Param("modelName") String modelName,
            @Param("rubricVersion") String rubricVersion,
            @Param("requestedAt") java.time.Instant requestedAt);
}
