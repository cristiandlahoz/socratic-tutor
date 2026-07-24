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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingActivityAiJobRepository extends JpaRepository<TrainingActivityAiJob, UUID> {
    @Query(value = """
            with candidate as (
              select id from training_activity_ai_job
              where attempt_count < max_attempts
                and ((status in ('PENDING', 'RETRYABLE') and available_at <= :now)
                  or (status = 'RUNNING' and lease_until < :now))
              order by (priority - extract(epoch from (:now - created_at)) / 60) asc, created_at asc
              for update skip locked limit 1
            )
            update training_activity_ai_job job
            set status = 'RUNNING', lease_until = :leaseUntil, attempt_count = attempt_count + 1,
                generation = generation + 1, updated_at = :now
            from candidate where job.id = candidate.id
            returning job.*
            """, nativeQuery = true)
    Optional<TrainingActivityAiJob> claimNext(@Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Modifying
    @Query("""
            update TrainingActivityAiJob job set job.status = :succeeded, job.leaseUntil = null,
              job.lastErrorCode = null, job.updatedAt = :now
            where job.id = :id and job.jobType = :jobType and job.status = :running
              and job.generation = :generation and job.leaseUntil >= :now
            """)
    int fenceSuccess(@Param("id") UUID id, @Param("jobType") TrainingActivityAiJobType jobType,
            @Param("running") TrainingActivityAiJobStatus running, @Param("succeeded") TrainingActivityAiJobStatus succeeded,
            @Param("generation") int generation, @Param("now") Instant now);

    @Modifying
    @Query("""
            update TrainingActivityAiJob job set job.status = :targetStatus, job.leaseUntil = null, job.inputVersion = :nextInputVersion,
              job.availableAt = :availableAt, job.lastErrorCode = :failureCode, job.updatedAt = :now
            where job.id = :id and job.jobType = :jobType and job.status = :running
              and job.generation = :generation and job.leaseUntil >= :now
            """)
    int fenceFailure(@Param("id") UUID id, @Param("jobType") TrainingActivityAiJobType jobType,
            @Param("running") TrainingActivityAiJobStatus running, @Param("targetStatus") TrainingActivityAiJobStatus targetStatus,
            @Param("generation") int generation, @Param("availableAt") Instant availableAt,
            @Param("failureCode") String failureCode, @Param("nextInputVersion") long nextInputVersion,
            @Param("now") Instant now);

    Optional<TrainingActivityAiJob> findFirstBySemanticKeyAndStatusInOrderByCreatedAtDesc(
            String semanticKey, List<TrainingActivityAiJobStatus> statuses);

    Optional<TrainingActivityAiJob> findTopBySemanticKeyOrderByGenerationDesc(String semanticKey);

    Optional<TrainingActivityAiJob> findFirstByAssignment_IdAndJobTypeInOrderByUpdatedAtDesc(
            UUID assignmentId, List<TrainingActivityAiJobType> jobTypes);

    Optional<TrainingActivityAiJob> findTopByAssignment_IdAndJobTypeOrderByGenerationDesc(
            UUID assignmentId, TrainingActivityAiJobType jobType);

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
            on conflict (semantic_key, generation) do nothing
            """, nativeQuery = true)
    int insertTutorJobIfAbsent(@Param("id") UUID id, @Param("jobType") String jobType, @Param("priority") int priority,
            @Param("activityId") UUID activityId, @Param("assignmentId") UUID assignmentId, @Param("turnId") UUID turnId,
            @Param("reportId") UUID reportId, @Param("inputVersion") long inputVersion, @Param("semanticKey") String semanticKey,
            @Param("maxAttempts") int maxAttempts, @Param("availableAt") Instant availableAt,
            @Param("createdAt") Instant createdAt, @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query(value = """
            insert into training_activity_ai_job (id, job_type, priority, training_activity_id, training_activity_assignment_id,
              training_activity_report_id, input_version, semantic_key, generation, status, attempt_count, max_attempts,
              available_at, created_at, updated_at)
            values (:id, 'FINAL_REPORT', :priority, :activityId, :assignmentId, :reportId, :inputVersion, :semanticKey,
              :generation, 'PENDING', 0, :maxAttempts, :availableAt, :createdAt, :updatedAt)
            on conflict (semantic_key, generation) do nothing
            """, nativeQuery = true)
    int insertFinalReportRetryIfAbsent(@Param("id") UUID id, @Param("priority") int priority,
            @Param("activityId") UUID activityId, @Param("assignmentId") UUID assignmentId, @Param("reportId") UUID reportId,
            @Param("inputVersion") long inputVersion, @Param("semanticKey") String semanticKey, @Param("generation") int generation,
            @Param("maxAttempts") int maxAttempts, @Param("availableAt") Instant availableAt,
            @Param("createdAt") Instant createdAt, @Param("updatedAt") Instant updatedAt);
}
