package com.wornux.data.repositories.training_activity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.training_activity.SafeBrowserAlert;
import com.wornux.data.entities.training_activity.SafeBrowserAlertStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafeBrowserAlertRepository extends JpaRepository<SafeBrowserAlert, UUID> {
    Optional<SafeBrowserAlert> findByProfessorTenantAccount_IdAndTrainingActivity_IdAndStatus(
            UUID professorTenantAccountId,
            UUID trainingActivityId,
            SafeBrowserAlertStatus status);

    @EntityGraph(attributePaths = {"trainingActivity"})
    List<SafeBrowserAlert> findByTrainingActivity_IdAndStatusOrderByUpdatedAtDesc(
            UUID trainingActivityId,
            SafeBrowserAlertStatus status);
}
