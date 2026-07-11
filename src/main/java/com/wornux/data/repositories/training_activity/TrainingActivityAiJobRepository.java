package com.wornux.data.repositories.training_activity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.training_activity.TrainingActivityAiJob;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingActivityAiJobRepository extends JpaRepository<TrainingActivityAiJob, UUID> {
    @Query("select job from TrainingActivityAiJob job where job.jobType = :jobType and ((job.status in :claimable and job.availableAt <= :now) or (job.status = :running and job.leaseUntil < :now)) order by job.priority asc, job.createdAt asc")
    List<TrainingActivityAiJob> findAvailable(
            @Param("jobType") TrainingActivityAiJobType jobType,
            @Param("claimable") List<TrainingActivityAiJobStatus> claimable,
            @Param("running") TrainingActivityAiJobStatus running,
            @Param("now") Instant now,
            Pageable pageable);

    @Modifying
    @Query("update TrainingActivityAiJob job set job.status = :running, job.leaseUntil = :leaseUntil, job.attemptCount = job.attemptCount + 1, job.updatedAt = :now where job.id = :id and ((job.status in :claimable and job.availableAt <= :now) or (job.status = :running and job.leaseUntil < :now))")
    int claim(@Param("id") UUID id, @Param("claimable") List<TrainingActivityAiJobStatus> claimable,
            @Param("running") TrainingActivityAiJobStatus running, @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);
}
