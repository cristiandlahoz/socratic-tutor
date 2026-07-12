package com.wornux.data.repositories.training_activity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingActivityAssignmentRepository extends JpaRepository<TrainingActivityAssignment, UUID> {
    @EntityGraph(attributePaths = "trainingActivity")
    List<TrainingActivityAssignment> findByGroupClassMember_IdOrderByUpdatedAtDesc(UUID groupClassMemberId);

    @EntityGraph(attributePaths = {"trainingActivity", "trainingActivity.groupClass", "groupClassMember", "groupClassMember.groupClass",
            "groupClassMember.tenantAccount", "groupClassMember.tenantAccount.account"})
    Optional<TrainingActivityAssignment> findWithTrainingActivityById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"trainingActivity", "trainingActivity.groupClass", "groupClassMember", "groupClassMember.groupClass"})
    @Query("select assignment from TrainingActivityAssignment assignment where assignment.id = :id")
    Optional<TrainingActivityAssignment> findLockedWithTrainingActivityById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"trainingActivity", "groupClassMember", "groupClassMember.tenantAccount", "groupClassMember.tenantAccount.account"})
    List<TrainingActivityAssignment> findByTrainingActivity_IdOrderByUpdatedAtDesc(UUID trainingActivityId);

    List<TrainingActivityAssignment> findByTrainingActivity_IdAndStatusNot(UUID trainingActivityId, TrainingActivityAssignmentStatus status);

    List<TrainingActivityAssignment> findBySafeBrowserSessionActiveTrue();

    long countByTrainingActivity_Id(UUID trainingActivityId);
}
