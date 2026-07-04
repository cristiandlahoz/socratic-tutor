package com.wornux.data.repositories.training_activity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("trainingActivityRepository")
public interface TrainingActivityRepository extends JpaRepository<TrainingActivity, UUID> {
    List<TrainingActivity> findByGroupClass_IdOrderByUpdatedAtDesc(UUID groupClassId);

    Optional<TrainingActivity> findFirstByCreatedByTenantAccount_IdAndStatus(UUID tenantAccountId, TrainingActivityLifecycleStatus status);
}
