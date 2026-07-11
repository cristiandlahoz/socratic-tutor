package com.wornux.data.repositories.training_activity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingActivityAssignmentRepository extends JpaRepository<TrainingActivityAssignment, UUID> {
    @EntityGraph(attributePaths = "trainingActivity")
    List<TrainingActivityAssignment> findByGroupClassMember_IdOrderByUpdatedAtDesc(UUID groupClassMemberId);

    @EntityGraph(attributePaths = {"trainingActivity", "trainingActivity.groupClass", "groupClassMember", "groupClassMember.groupClass"})
    Optional<TrainingActivityAssignment> findWithTrainingActivityById(UUID id);

    @EntityGraph(attributePaths = {"trainingActivity", "groupClassMember", "groupClassMember.tenantAccount", "groupClassMember.tenantAccount.account"})
    List<TrainingActivityAssignment> findByTrainingActivity_IdOrderByUpdatedAtDesc(UUID trainingActivityId);

    List<TrainingActivityAssignment> findByTrainingActivity_IdAndStatusNot(UUID trainingActivityId, TrainingActivityAssignmentStatus status);

    List<TrainingActivityAssignment> findBySafeBrowserSessionActiveTrue();

    long countByTrainingActivity_Id(UUID trainingActivityId);
}
