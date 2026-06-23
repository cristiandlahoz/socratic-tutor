package com.wornux.data.repositories.training_activity;

import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingActivityAssignmentRepository extends JpaRepository<TrainingActivityAssignment, UUID> {
    List<TrainingActivityAssignment> findByGroupClassMember_IdOrderByUpdatedAtDesc(UUID groupClassMemberId);
}
