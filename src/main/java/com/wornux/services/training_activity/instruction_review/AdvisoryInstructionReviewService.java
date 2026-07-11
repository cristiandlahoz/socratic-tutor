package com.wornux.services.training_activity.instruction_review;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.training_activity.*;
import com.wornux.data.entities.training_activity.instruction_review.*;
import com.wornux.data.repositories.training_activity.TrainingActivityAiJobRepository;
import com.wornux.data.repositories.training_activity.instruction_review.TrainingInstructionReviewOverrideRepository;
import com.wornux.data.repositories.training_activity.instruction_review.TrainingInstructionReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persists advisory review requests and results; it never calls a model. */
@Service
public class AdvisoryInstructionReviewService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvisoryInstructionReviewService.class);
    private static final int REVIEW_PRIORITY = 100;
    private static final int MAX_ATTEMPTS = 3;

    private final TrainingInstructionReviewRepository reviewRepository;
    private final TrainingInstructionReviewOverrideRepository overrideRepository;
    private final TrainingActivityAiJobRepository jobRepository;
    private final InstructionReviewService reviewEngine;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public AdvisoryInstructionReviewService(
            TrainingInstructionReviewRepository reviewRepository,
            TrainingInstructionReviewOverrideRepository overrideRepository,
            TrainingActivityAiJobRepository jobRepository,
            InstructionReviewService reviewEngine) {
        this.reviewRepository = reviewRepository;
        this.overrideRepository = overrideRepository;
        this.jobRepository = jobRepository;
        this.reviewEngine = reviewEngine;
    }

    @Transactional
    public InstructionReviewSnapshotDto request(
            UUID candidateId,
            TrainingActivity activity,
            UUID groupClassId,
            UUID actorMemberId,
            String title,
            String instructions) {
        requireText(title, "Title");
        requireText(instructions, "Instructions");
        var instructionsHash = reviewEngine.hashNormalizedInstructions(instructions);
        var modelName = reviewEngine.currentModelName();
        var rubricVersion = reviewEngine.promptVersion();
        var existing = activity == null
                ? java.util.Optional.<TrainingInstructionReview>empty()
                : reviewRepository.findFirstByTrainingActivity_IdAndGroupClass_IdAndRequestedByGroupClassMember_IdAndInstructionsHashAndModelNameAndRubricVersionOrderByRequestedAtDesc(
                        activity.getId(), groupClassId, actorMemberId, instructionsHash, modelName, rubricVersion);
        if (existing.isEmpty()) {
            existing = reviewRepository.findByCandidateIdAndGroupClass_IdAndRequestedByGroupClassMember_IdAndInstructionsHashAndModelNameAndRubricVersion(
                    candidateId, groupClassId, actorMemberId, instructionsHash, modelName, rubricVersion);
        }
        if (existing.isPresent()) {
            var review = existing.get();
            if (activity != null && review.getTrainingActivity() == null) {
                review.setTrainingActivity(activity);
            }
            else if (activity != null && !activity.getId().equals(review.getTrainingActivity().getId())) {
                throw new IllegalArgumentException("The review candidate is already attached to another training activity.");
            }
            return snapshot(review, instructionsHash);
        }

        var now = Instant.now();
        var inserted = reviewRepository.insertIfAbsent(
                UUID.randomUUID(), candidateId, activity == null ? null : activity.getId(), groupClassId, actorMemberId,
                normalize(title), normalize(instructions), instructionsHash, modelName, rubricVersion, now);
        var review = reviewRepository.findByCandidateIdAndGroupClass_IdAndRequestedByGroupClassMember_IdAndInstructionsHashAndModelNameAndRubricVersion(
                        candidateId, groupClassId, actorMemberId, instructionsHash, modelName, rubricVersion)
                .orElseThrow(() -> new IllegalStateException("The instruction review was not persisted."));
        if (inserted == 0) {
            LOGGER.info("instructionReview request recovered an idempotency conflict: candidateId={} groupClassId={} actorMemberId={}",
                    candidateId, groupClassId, actorMemberId);
            return snapshot(review, instructionsHash);
        }

        var job = new TrainingActivityAiJob();
        job.setId(UUID.randomUUID());
        job.setJobType(TrainingActivityAiJobType.INSTRUCTION_REVIEW);
        job.setPriority(REVIEW_PRIORITY);
        job.setTrainingActivity(activity);
        job.setInstructionReview(review);
        job.setInputVersion(0);
        job.setSemanticKey(semanticKey(candidateId, groupClassId, actorMemberId, instructionsHash, rubricVersion, modelName));
        job.setGeneration(1);
        job.setStatus(TrainingActivityAiJobStatus.PENDING);
        job.setAttemptCount(0);
        job.setMaxAttempts(MAX_ATTEMPTS);
        job.setAvailableAt(now);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobRepository.save(job);
        return snapshot(review, instructionsHash);
    }

    @Transactional(readOnly = true)
    public InstructionReviewSnapshotDto current(UUID activityId, String instructions) {
        var currentHash = reviewEngine.hashNormalizedInstructions(instructions);
        return reviewRepository.findFirstByTrainingActivity_IdOrderByRequestedAtDesc(activityId)
                .map(review -> snapshot(review, currentHash))
                .orElse(new InstructionReviewSnapshotDto(
                        activityId, currentHash, InstructionReviewStatus.IDLE, null, false,
                        "No AI recommendation is available for these instructions.", false, false, List.of(), "", Instant.now()));
    }

    @Transactional
    public void recordOverride(
            TrainingActivity activity,
            UUID actorMemberId,
            String instructions,
            InstructionReviewOverrideAction action) {
        var currentHash = reviewEngine.hashNormalizedInstructions(instructions);
        var review = reviewRepository.findFirstByTrainingActivity_IdAndInstructionsHashAndModelNameAndRubricVersionOrderByRequestedAtDesc(
                activity.getId(), currentHash, reviewEngine.currentModelName(), reviewEngine.promptVersion()).orElse(null);
        var override = new TrainingInstructionReviewOverride();
        override.setId(UUID.randomUUID());
        override.setTrainingActivity(activity);
        override.setTrainingInstructionReview(review);
        override.setInstructionsHash(currentHash);
        override.setAction(action);
        override.setActorGroupClassMember(reference(GroupClassMember.class, actorMemberId));
        override.setCreatedAt(Instant.now());
        overrideRepository.save(override);
    }

    @Transactional
    public ReviewWork claim(UUID jobId, Instant now, Instant leaseUntil) {
        var reclaimingLease = jobRepository.findById(jobId)
                .filter(job -> job.getStatus() == TrainingActivityAiJobStatus.RUNNING)
                .filter(job -> job.getLeaseUntil() != null && job.getLeaseUntil().isBefore(now))
                .isPresent();
        var claimed = jobRepository.claim(jobId,
                List.of(TrainingActivityAiJobStatus.PENDING, TrainingActivityAiJobStatus.RETRYABLE),
                TrainingActivityAiJobStatus.RUNNING, leaseUntil, now);
        if (claimed == 0) {
            LOGGER.debug("instructionReview claim missed: jobId={}", jobId);
            return null;
        }
        if (reclaimingLease) {
            LOGGER.warn("instructionReview reclaimed an expired lease: jobId={}", jobId);
        }
        var job = jobRepository.findById(jobId).orElseThrow();
        var review = job.getInstructionReview();
        return new ReviewWork(job.getId(), review.getId(), review.getTitleSnapshot(), review.getInstructionsSnapshot());
    }

    @Transactional(readOnly = true)
    public List<UUID> availableJobIds(Instant now, int limit) {
        return jobRepository.findAvailable(
                TrainingActivityAiJobType.INSTRUCTION_REVIEW,
                List.of(TrainingActivityAiJobStatus.PENDING, TrainingActivityAiJobStatus.RETRYABLE),
                TrainingActivityAiJobStatus.RUNNING,
                now,
                org.springframework.data.domain.PageRequest.of(0, limit))
                .stream().map(TrainingActivityAiJob::getId).toList();
    }

    @Transactional
    public void applySuccess(UUID jobId, InstructionReviewResult result) {
        var job = jobRepository.findById(jobId).orElseThrow();
        if (job.getStatus() != TrainingActivityAiJobStatus.RUNNING
                || job.getLeaseUntil() == null
                || job.getLeaseUntil().isBefore(Instant.now())) {
            return;
        }
        var review = job.getInstructionReview();
        if (!review.getInstructionsHash().equals(reviewEngine.hashNormalizedInstructions(review.getInstructionsSnapshot()))) {
            markStale(job, review);
            return;
        }
        review.setExecutionStatus(TrainingInstructionReviewExecutionStatus.SUCCEEDED);
        review.setOutcome(toOutcome(result));
        review.setSummary(result.summary());
        review.setIssuesJson(writeIssues(validIssues(result.issues(), review.getInstructionsSnapshot())));
        review.setImprovedInstructions(result.improvedInstructions());
        review.setCompletedAt(Instant.now());
        job.setStatus(TrainingActivityAiJobStatus.SUCCEEDED);
        job.setLeaseUntil(null);
        job.setUpdatedAt(Instant.now());
    }

    @Transactional
    public boolean applyFailure(UUID jobId, String failureCode) {
        var job = jobRepository.findById(jobId).orElseThrow();
        if (job.getStatus() != TrainingActivityAiJobStatus.RUNNING) {
            return false;
        }
        var retry = job.getAttemptCount() < job.getMaxAttempts();
        job.setStatus(retry ? TrainingActivityAiJobStatus.RETRYABLE : TrainingActivityAiJobStatus.FAILED);
        job.setAvailableAt(Instant.now().plusSeconds(retry ? job.getAttemptCount() * 5L : 0));
        job.setLeaseUntil(null);
        job.setLastErrorCode(failureCode);
        job.setUpdatedAt(Instant.now());
        if (!retry) {
            var review = job.getInstructionReview();
            review.setExecutionStatus(TrainingInstructionReviewExecutionStatus.FAILED);
            review.setFailureCode(failureCode);
            review.setCompletedAt(Instant.now());
            LOGGER.warn("instructionReview retries exhausted: jobId={} failureCode={} attempts={}",
                    jobId, failureCode, job.getAttemptCount());
        }
        else {
            LOGGER.info("instructionReview retry scheduled: jobId={} failureCode={} attempt={}/{}", jobId,
                    failureCode, job.getAttemptCount(), job.getMaxAttempts());
        }
        return retry;
    }

    private void markStale(TrainingActivityAiJob job, TrainingInstructionReview review) {
        job.setStatus(TrainingActivityAiJobStatus.SUCCEEDED);
        job.setLeaseUntil(null);
        job.setLastErrorCode("STALE_RESULT");
        job.setUpdatedAt(Instant.now());
        review.setExecutionStatus(TrainingInstructionReviewExecutionStatus.FAILED);
        review.setFailureCode("STALE_RESULT");
        review.setCompletedAt(Instant.now());
    }

    private InstructionReviewSnapshotDto snapshot(TrainingInstructionReview review, String currentHash) {
        var stale = !review.getInstructionsHash().equals(currentHash);
        var status = stale ? InstructionReviewStatus.STALE : switch (review.getExecutionStatus()) {
            case PENDING -> InstructionReviewStatus.REVIEWING;
            case FAILED -> InstructionReviewStatus.UNAVAILABLE;
            case SUCCEEDED -> review.getOutcome() == InstructionReviewOutcome.INVALID
                    ? InstructionReviewStatus.LOCAL_INVALID : InstructionReviewStatus.COMPLETED;
        };
        var outcome = review.getOutcome() == InstructionReviewOutcome.GOOD ? InstructionQualityStatus.GOOD
                : review.getOutcome() == InstructionReviewOutcome.NEEDS_IMPROVEMENT ? InstructionQualityStatus.NEEDS_IMPROVEMENT : null;
        var message = stale ? "The review belongs to older instructions. Request a new review."
                : review.getSummary() == null ? status == InstructionReviewStatus.REVIEWING ? "Reviewing instructions…" : "AI review is unavailable."
                : review.getSummary();
        return new InstructionReviewSnapshotDto(review.getTrainingActivity() == null ? null : review.getTrainingActivity().getId(),
                review.getInstructionsHash(), status, outcome, outcome == InstructionQualityStatus.GOOD, message,
                false, false, readIssues(review.getIssuesJson(), review.getInstructionsSnapshot()), review.getImprovedInstructions(), review.getCompletedAt() == null ? review.getRequestedAt() : review.getCompletedAt());
    }

    private InstructionReviewOutcome toOutcome(InstructionReviewResult result) {
        if (result.qualityStatus() == InstructionQualityStatus.GOOD) return InstructionReviewOutcome.GOOD;
        if (result.qualityStatus() == InstructionQualityStatus.NEEDS_IMPROVEMENT) return InstructionReviewOutcome.NEEDS_IMPROVEMENT;
        return InstructionReviewOutcome.INVALID;
    }

    private String writeIssues(List<InstructionReviewIssue> issues) {
        try { return objectMapper.writeValueAsString(issues == null ? List.of() : issues); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize review issues", exception); }
    }

    private List<InstructionLintIssueDto> readIssues(String issues, String reviewedInstructions) {
        if (issues == null || issues.isBlank()) return List.of();
        try {
            return objectMapper.readValue(issues, new TypeReference<List<InstructionReviewIssue>>() { })
                    .stream().flatMap(issue -> validIssue(issue, reviewedInstructions).stream())
                    .map(issue -> new InstructionLintIssueDto(issue.id(), issue.category(), issue.severity().name(), issue.startOffset(), issue.endOffset(), issue.message(), issue.whyItMatters(), issue.suggestedReplacement(), issue.suggestionReason())).toList();
        } catch (JsonProcessingException exception) {
            LOGGER.warn("instructionReview discarded malformed persisted issues");
            return List.of();
        }
    }

    private List<InstructionReviewIssue> validIssues(List<InstructionReviewIssue> issues, String reviewedInstructions) {
        var valid = new ArrayList<InstructionReviewIssue>();
        for (var issue : issues == null ? List.<InstructionReviewIssue>of() : issues) {
            validIssue(issue, reviewedInstructions).ifPresent(valid::add);
        }
        return valid;
    }

    private java.util.Optional<InstructionReviewIssue> validIssue(InstructionReviewIssue issue, String reviewedInstructions) {
        if (issue == null || issue.severity() == null) return java.util.Optional.empty();
        var start = issue.startOffset();
        var end = issue.endOffset();
        if ((start == null) != (end == null)) return java.util.Optional.empty();
        if (start == null) {
            return java.util.Optional.of(new InstructionReviewIssue(issue.id(), issue.severity(), issue.category(),
                    issue.problemText(), null, null, issue.message(), issue.whyItMatters(), "", issue.suggestionReason()));
        }
        if (start < 0 || end <= start || end > reviewedInstructions.length()) return java.util.Optional.empty();
        var replacement = issue.suggestedReplacement() == null ? "" : issue.suggestedReplacement().trim();
        if (replacement.indexOf('\u0000') >= 0) return java.util.Optional.empty();
        return java.util.Optional.of(new InstructionReviewIssue(issue.id(), issue.severity(), issue.category(),
                reviewedInstructions.substring(start, end), start, end, issue.message(), issue.whyItMatters(), replacement,
                issue.suggestionReason()));
    }

    private static <T> T reference(Class<T> type, UUID id) {
        try { var value = type.getDeclaredConstructor().newInstance(); type.getMethod("setId", UUID.class).invoke(value, id); return value; }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException("Cannot create entity reference", exception); }
    }

    private static String semanticKey(UUID candidateId, UUID groupClassId, UUID actorMemberId, String hash, String rubric, String model) { return groupClassId + ":" + actorMemberId + ":" + candidateId + ":" + hash + ":" + rubric + ":" + model; }
    private static String normalize(String value) { return value.trim().replace("\r\n", "\n"); }
    private static void requireText(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required."); }
    public record ReviewWork(UUID jobId, UUID reviewId, String title, String instructions) { }
}
