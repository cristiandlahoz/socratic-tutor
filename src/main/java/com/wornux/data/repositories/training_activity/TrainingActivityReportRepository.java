package com.wornux.data.repositories.training_activity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.TrainingActivityReport;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingActivityReportRepository extends JpaRepository<TrainingActivityReport, UUID> {
    Optional<TrainingActivityReport> findByAssignment_Id(UUID assignmentId);

    @Modifying
    @Query(value = """
            insert into training_activity_report (id, training_activity_assignment_id, status, evidence_status, model_name,
              prompt_version, attempt_count, requested_at, updated_at)
            values (:id, :assignmentId, 'PENDING', :evidenceStatus, :modelName, :promptVersion, 0, :now, :now)
            on conflict (training_activity_assignment_id) do nothing
            """, nativeQuery = true)
    int insertPendingIfAbsent(@Param("id") UUID id, @Param("assignmentId") UUID assignmentId,
            @Param("evidenceStatus") String evidenceStatus, @Param("modelName") String modelName,
            @Param("promptVersion") String promptVersion, @Param("now") Instant now);
}
