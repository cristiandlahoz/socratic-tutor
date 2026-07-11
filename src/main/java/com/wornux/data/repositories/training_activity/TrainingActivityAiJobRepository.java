package com.wornux.data.repositories.training_activity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    @Query("select job from TrainingActivityAiJob job where job.jobType = :jobType and job.attemptCount < job.maxAttempts and ((job.status in :claimable and job.availableAt <= :now) or (job.status = :running and job.leaseUntil < :now)) order by job.priority asc, job.createdAt asc")
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

    @Modifying
    @Query("update TrainingActivityAiJob job set job.status = :running, job.leaseUntil = :leaseUntil, job.attemptCount = job.attemptCount + 1, job.generation = job.generation + 1, job.updatedAt = :now where job.id = :id and job.attemptCount < job.maxAttempts and ((job.status in :claimable and job.availableAt <= :now) or (job.status = :running and job.leaseUntil < :now))")
    int claimTutor(@Param("id") UUID id, @Param("claimable") List<TrainingActivityAiJobStatus> claimable,
            @Param("running") TrainingActivityAiJobStatus running, @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    @Query("select job from TrainingActivityAiJob job where job.jobType in :jobTypes and job.attemptCount < job.maxAttempts and ((job.status in :claimable and job.availableAt <= :now) or (job.status = :running and job.leaseUntil < :now)) order by job.priority asc, job.createdAt asc")
    List<TrainingActivityAiJob> findAvailableByTypes(
            @Param("jobTypes") List<TrainingActivityAiJobType> jobTypes,
            @Param("claimable") List<TrainingActivityAiJobStatus> claimable,
            @Param("running") TrainingActivityAiJobStatus running,
            @Param("now") Instant now,
            Pageable pageable);

    Optional<TrainingActivityAiJob> findFirstBySemanticKeyAndStatusInOrderByCreatedAtDesc(
            String semanticKey, List<TrainingActivityAiJobStatus> statuses);

    Optional<TrainingActivityAiJob> findFirstByAssignment_IdAndJobTypeInOrderByUpdatedAtDesc(
            UUID assignmentId, List<TrainingActivityAiJobType> jobTypes);

    @Query("select job from TrainingActivityAiJob job where job.jobType in :jobTypes and job.status = :running and job.leaseUntil < :now and job.attemptCount >= job.maxAttempts")
    List<TrainingActivityAiJob> findExpiredAtAttemptLimit(@Param("jobTypes") List<TrainingActivityAiJobType> jobTypes,
            @Param("running") TrainingActivityAiJobStatus running, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            insert into training_activity_ai_job (id, job_type, priority, training_activity_id, training_activity_assignment_id,
              training_activity_turn_id, training_activity_report_id, input_version, semantic_key, generation, status,
              attempt_count, max_attempts, available_at, created_at, updated_at)
            values (:id, :jobType, :priority, :activityId, :assignmentId, :turnId, :reportId, :inputVersion, :semanticKey,
              0, 'PENDING', 0, :maxAttempts, :availableAt, :createdAt, :updatedAt)
            on conflict (semantic_key) do nothing
            """, nativeQuery = true)
    int insertTutorJobIfAbsent(@Param("id") UUID id, @Param("jobType") String jobType, @Param("priority") int priority,
            @Param("activityId") UUID activityId, @Param("assignmentId") UUID assignmentId, @Param("turnId") UUID turnId,
            @Param("reportId") UUID reportId, @Param("inputVersion") long inputVersion, @Param("semanticKey") String semanticKey,
            @Param("maxAttempts") int maxAttempts, @Param("availableAt") Instant availableAt,
            @Param("createdAt") Instant createdAt, @Param("updatedAt") Instant updatedAt);
}
