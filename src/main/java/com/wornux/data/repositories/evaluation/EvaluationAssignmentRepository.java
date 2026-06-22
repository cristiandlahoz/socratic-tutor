package com.wornux.data.repositories.evaluation;

import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.evaluation.EvaluationAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationAssignmentRepository extends JpaRepository<EvaluationAssignment, UUID> {
    List<EvaluationAssignment> findByGroupClassMember_IdOrderByUpdatedAtDesc(UUID groupClassMemberId);
}
