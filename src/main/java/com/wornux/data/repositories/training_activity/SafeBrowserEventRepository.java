package com.wornux.data.repositories.training_activity;

import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.training_activity.SafeBrowserEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafeBrowserEventRepository extends JpaRepository<SafeBrowserEvent, UUID> {
    @EntityGraph(attributePaths = {"assignment", "assignment.groupClassMember", "assignment.groupClassMember.tenantAccount", "assignment.groupClassMember.tenantAccount.account"})
    List<SafeBrowserEvent> findByAssignment_TrainingActivity_IdOrderByOccurredAtDesc(UUID trainingActivityId);
}
