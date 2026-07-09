package com.wornux.services.training_activity.instruction_review;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewCacheEntry;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.data.repositories.training_activity.instruction_review.InstructionReviewCacheRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstructionReviewCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstructionReviewCoordinator.class);
    private static final TypeReference<List<InstructionLintIssueDto>> ISSUE_LIST_TYPE = new TypeReference<>() {
    };
    private static final Set<InstructionReviewStatus> REUSABLE_REVIEW_STATUSES = Set.of(
            InstructionReviewStatus.COMPLETED,
            InstructionReviewStatus.COMPLETED_FROM_CACHE,
            InstructionReviewStatus.SKIPPED_NO_CHANGES,
            InstructionReviewStatus.READY_TO_SAVE,
            InstructionReviewStatus.NEEDS_USER_FIX);

    private final TrainingActivityRepository trainingActivityRepository;
    private final InstructionReviewCacheRepository reviewCacheRepository;
    private final InstructionReviewService instructionReviewService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public InstructionReviewCoordinator(
            TrainingActivityRepository trainingActivityRepository,
            InstructionReviewCacheRepository reviewCacheRepository,
            InstructionReviewService instructionReviewService) {
        this.trainingActivityRepository = trainingActivityRepository;
        this.reviewCacheRepository = reviewCacheRepository;
        this.instructionReviewService = instructionReviewService;
    }

    public ReviewBeforeSaveDecision reviewBeforeSave(TrainingActivity currentActivity, String title, String instructions) {
        var startedAt = System.nanoTime();
        LOGGER.info(
                "reviewBeforeSave started: activityId={} titleLength={} instructionLength={}",
                currentActivity == null ? null : currentActivity.getId(),
                title == null ? 0 : title.trim().length(),
                instructions == null ? 0 : instructions.trim().length());
        if (instructions == null || instructions.isBlank()) {
            LOGGER.info("reviewBeforeSave detected blank instructions; delegating to local invalid review");
            var review = instructionReviewService.review(title, instructions);
            var snapshot = snapshot(null, "", InstructionReviewStatus.LOCAL_INVALID, review, false, false);
            logDecision(currentActivity == null ? null : currentActivity.getId(), snapshot, startedAt);
            return new ReviewBeforeSaveDecision(snapshot, review, false);
        }

        var reviewHash = instructionReviewService.reviewHash(title, instructions);
        LOGGER.info("reviewBeforeSave computed reviewHash={}", reviewHash);
        if (currentActivity != null && canReuseCurrentActivityReview(currentActivity, reviewHash)) {
            LOGGER.info("reviewBeforeSave reusing current activity review: activityId={} reviewHash={}", currentActivity.getId(), reviewHash);
            var review = toReviewResult(currentActivity);
            var reusableStatus = currentActivity.getInstructionReviewQualityStatus() == InstructionQualityStatus.GOOD
                    ? InstructionReviewStatus.READY_TO_SAVE
                    : InstructionReviewStatus.SKIPPED_NO_CHANGES;
            var snapshot = snapshot(currentActivity.getId(), reviewHash, reusableStatus, review, false, false);
            logDecision(currentActivity.getId(), snapshot, startedAt);
            return new ReviewBeforeSaveDecision(snapshot, review, snapshot.canSave());
        }

        var cached = reviewCacheRepository.findById(reviewHash);
        if (cached.isPresent()) {
            LOGGER.info("reviewBeforeSave found cached review: reviewHash={}", reviewHash);
            var review = toReviewResult(cached.get(), reviewHash);
            var snapshot = snapshot(
                    currentActivity == null ? null : currentActivity.getId(),
                    reviewHash,
                    InstructionReviewStatus.COMPLETED_FROM_CACHE,
                    review,
                    false,
                    true);
            logDecision(currentActivity == null ? null : currentActivity.getId(), snapshot, startedAt);
            return new ReviewBeforeSaveDecision(snapshot, review, snapshot.canSave());
        }

        LOGGER.info("reviewBeforeSave invoking model-backed instruction review: reviewHash={}", reviewHash);
        var review = instructionReviewService.review(title, instructions);
        LOGGER.info(
                "reviewBeforeSave model review completed: reviewHash={} qualityStatus={} issuesCount={}",
                reviewHash,
                review.qualityStatus(),
                review.issues() == null ? 0 : review.issues().size());
        var reviewStatus = review.canSave()
                ? InstructionReviewStatus.READY_TO_SAVE
                : InstructionReviewStatus.NEEDS_USER_FIX;
        var snapshot = snapshot(
                currentActivity == null ? null : currentActivity.getId(),
                reviewHash,
                reviewStatus,
                review,
                true,
                false);
        LOGGER.info("reviewBeforeSave saving review result in cache: reviewHash={} reviewStatus={}", reviewHash, reviewStatus);
        saveCache(title, instructions, reviewHash, snapshot, review);
        logDecision(currentActivity == null ? null : currentActivity.getId(), snapshot, startedAt);
        return new ReviewBeforeSaveDecision(snapshot, review, snapshot.canSave());
    }

    @Transactional(readOnly = true)
    public InstructionReviewSnapshotDto snapshot(TrainingActivity activity) {
        return snapshot(
                activity == null ? null : activity.getId(),
                activity == null ? "" : activity.getInstructionReviewHash(),
                activity == null || activity.getInstructionReviewStatus() == null ? InstructionReviewStatus.IDLE : activity.getInstructionReviewStatus(),
                activity == null ? null : toReviewResult(activity),
                false,
                activity != null && activity.getInstructionReviewStatus() == InstructionReviewStatus.COMPLETED_FROM_CACHE);
    }

    public boolean canReuseCurrentActivityReview(TrainingActivity activity, String currentHash) {
        return currentHash != null
                && currentHash.equals(activity.getInstructionReviewHash())
                && instructionReviewService.promptVersion().equals(activity.getInstructionReviewPromptVersion())
                && instructionReviewService.currentModelName().equals(activity.getInstructionReviewModelName())
                && REUSABLE_REVIEW_STATUSES.contains(activity.getInstructionReviewStatus());
    }

    public boolean hasCurrentGoodInstructionReview(TrainingActivity activity) {
        var currentHash = instructionReviewService.reviewHash(activity.getTitle(), activity.getInstructions());
        var currentInstructionsHash = instructionReviewService.hashNormalizedInstructions(activity.getInstructions());
        return currentHash.equals(activity.getInstructionReviewHash())
                && currentInstructionsHash.equals(activity.getInstructionReviewInstructionsHash())
                && activity.getInstructionReviewQualityStatus() == InstructionQualityStatus.GOOD
                && Boolean.TRUE.equals(activity.getInstructionReviewValidInstruction())
                && instructionReviewService.promptVersion().equals(activity.getInstructionReviewPromptVersion())
                && instructionReviewService.currentModelName().equals(activity.getInstructionReviewModelName())
                && REUSABLE_REVIEW_STATUSES.contains(activity.getInstructionReviewStatus());
    }

    public InstructionReviewSnapshotDto unavailableSnapshot(TrainingActivity activity, InstructionReviewResult review) {
        var reviewHash = review != null && review.instructionsHash() != null && !review.instructionsHash().isBlank()
                ? review.instructionsHash()
                : activity == null
                        ? ""
                        : instructionReviewService.reviewHash(activity.getTitle(), activity.getInstructions());
        return snapshot(
                activity == null ? null : activity.getId(),
                reviewHash,
                InstructionReviewStatus.UNAVAILABLE,
                review,
                true,
                false);
    }

    public void applyPersistedReview(
            TrainingActivity activity,
            InstructionReviewSnapshotDto snapshot,
            InstructionReviewResult reviewResult) {
        activity.setInstructionReviewHash(snapshot.reviewHash());
        activity.setInstructionReviewInstructionsHash(instructionReviewService.hashNormalizedInstructions(activity.getInstructions()));
        activity.setInstructionReviewStatus(toPersistedStatus(snapshot.reviewStatus()));
        activity.setInstructionReviewQualityStatus(snapshot.qualityStatus());
        activity.setInstructionReviewValidInstruction(reviewResult.validInstruction());
        activity.setInstructionReviewSummary(reviewResult.summary());
        activity.setInstructionReviewIssuesJson(writeIssues(snapshot.issues()));
        activity.setInstructionReviewMessage(snapshot.message());
        activity.setInstructionReviewImprovedInstructions(reviewResult.improvedInstructions());
        activity.setInstructionReviewPromptVersion(instructionReviewService.promptVersion());
        activity.setInstructionReviewModelName(reviewResult.modelName());
        activity.setInstructionReviewRubricVersion(reviewResult.rubricVersion());
        activity.setInstructionReviewedAt(snapshot.reviewedAt());
    }

    public InstructionReviewResult toReviewResult(TrainingActivity activity) {
        var issues = readIssues(activity.getInstructionReviewIssuesJson()).stream().map(this::toReviewIssue).toList();
        var qualityStatus = activity.getInstructionReviewQualityStatus();
        var validInstruction = Boolean.TRUE.equals(activity.getInstructionReviewValidInstruction())
                || qualityStatus == InstructionQualityStatus.GOOD
                || qualityStatus == InstructionQualityStatus.NEEDS_IMPROVEMENT;
        var canSave = validInstruction && qualityStatus == InstructionQualityStatus.GOOD;
        return new InstructionReviewResult(
                validInstruction,
                qualityStatus,
                canSave,
                canSave,
                activity.getInstructionReviewMessage() == null ? "" : activity.getInstructionReviewMessage(),
                activity.getInstructionReviewMessage() == null ? "" : activity.getInstructionReviewMessage(),
                issues,
                activity.getInstructionReviewImprovedInstructions() == null ? "" : activity.getInstructionReviewImprovedInstructions(),
                "",
                activity.getInstructionReviewHash(),
                activity.getInstructionReviewedAt() == null ? Instant.now() : activity.getInstructionReviewedAt(),
                activity.getInstructionReviewModelName(),
                activity.getInstructionReviewPromptVersion());
    }

    private InstructionReviewResult toReviewResult(InstructionReviewCacheEntry entry, String reviewHash) {
        var issues = readIssues(entry.getIssuesJson()).stream().map(this::toReviewIssue).toList();
        var qualityStatus = entry.getQualityStatus();
        var validInstruction = Boolean.TRUE.equals(entry.getValidInstruction())
                || qualityStatus == InstructionQualityStatus.GOOD
                || qualityStatus == InstructionQualityStatus.NEEDS_IMPROVEMENT;
        var canSave = validInstruction && qualityStatus == InstructionQualityStatus.GOOD;
        return new InstructionReviewResult(
                validInstruction,
                qualityStatus,
                canSave,
                canSave,
                entry.getReviewMessage() == null ? "" : entry.getReviewMessage(),
                entry.getReviewMessage() == null ? "" : entry.getReviewMessage(),
                issues,
                entry.getRecreatedInstructions() == null ? "" : entry.getRecreatedInstructions(),
                "",
                reviewHash,
                entry.getCompletedAt() == null ? Instant.now() : entry.getCompletedAt(),
                entry.getModelName(),
                entry.getPromptVersion());
    }

    private void saveCache(
            String title,
            String instructions,
            String reviewHash,
            InstructionReviewSnapshotDto snapshot,
            InstructionReviewResult review) {
        var entry = new InstructionReviewCacheEntry();
        entry.setReviewHash(reviewHash);
        entry.setPromptVersion(instructionReviewService.promptVersion());
        entry.setModelName(review.modelName());
        entry.setNormalizedTitleHash(instructionReviewService.hashNormalizedTitle(title));
        entry.setNormalizedInstructionsHash(instructionReviewService.hashNormalizedInstructions(instructions));
        entry.setReviewStatus(InstructionReviewStatus.COMPLETED);
        entry.setQualityStatus(snapshot.qualityStatus());
        entry.setValidInstruction(review.validInstruction());
        entry.setIssuesJson(writeIssues(snapshot.issues()));
        entry.setReviewMessage(snapshot.message());
        entry.setRecreatedInstructions(review.improvedInstructions());
        entry.setCreatedAt(Instant.now());
        entry.setCompletedAt(snapshot.reviewedAt());
        reviewCacheRepository.save(entry);
    }

    private InstructionReviewSnapshotDto snapshot(
            UUID activityId,
            String reviewHash,
            InstructionReviewStatus reviewStatus,
            InstructionReviewResult review,
            boolean modelCalled,
            boolean fromCache) {
        var issues = review == null ? List.<InstructionLintIssueDto>of() : review.issues().stream().map(this::toIssueDto).toList();
        var requiresVisibleFeedbackBeforePersist = fromCache
                && review != null
                && Boolean.TRUE.equals(review.validInstruction())
                && review.qualityStatus() == InstructionQualityStatus.GOOD
                && !issues.isEmpty();
        var canSave = review != null && review.canSave() && !requiresVisibleFeedbackBeforePersist;
        return new InstructionReviewSnapshotDto(
                activityId,
                reviewHash,
                reviewStatus,
                review == null ? null : review.qualityStatus(),
                canSave,
                resolveMessage(reviewStatus, review),
                modelCalled,
                fromCache,
                issues,
                review == null ? "" : review.improvedInstructions(),
                review == null ? Instant.now() : review.reviewedAt());
    }

    private String resolveMessage(InstructionReviewStatus reviewStatus, InstructionReviewResult review) {
        if (review != null && review.summary() != null && !review.summary().isBlank()) {
            return review.summary();
        }
        if (reviewStatus == InstructionReviewStatus.LOCAL_INVALID) {
            return "Escribe instrucciones para poder revisarlas.";
        }
        return "";
    }

    private InstructionLintIssueDto toIssueDto(InstructionReviewIssue issue) {
        return new InstructionLintIssueDto(
                issue.id(),
                normalizeCode(issue.category()),
                issue.severity().name(),
                issue.startOffset(),
                issue.endOffset(),
                issue.message(),
                issue.whyItMatters(),
                issue.suggestedReplacement(),
                issue.suggestionReason());
    }

    private InstructionReviewIssue toReviewIssue(InstructionLintIssueDto issue) {
        return new InstructionReviewIssue(
                issue.issueKey(),
                InstructionReviewIssueSeverity.valueOf(issue.severity()),
                issue.code(),
                "",
                issue.startOffset(),
                issue.endOffset(),
                issue.message(),
                issue.whyItMatters(),
                issue.suggestedReplacement(),
                issue.suggestionReason());
    }

    private String normalizeCode(String value) {
        return value == null ? "MISSING_EXPECTED_EVIDENCE" : value.trim().toUpperCase(Locale.ROOT);
    }

    private InstructionReviewStatus toPersistedStatus(InstructionReviewStatus snapshotStatus) {
        return switch (snapshotStatus) {
            case READY_TO_SAVE, NEEDS_USER_FIX -> InstructionReviewStatus.COMPLETED;
            default -> snapshotStatus;
        };
    }

    private List<InstructionLintIssueDto> readIssues(String issuesJson) {
        if (issuesJson == null || issuesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(issuesJson, ISSUE_LIST_TYPE);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize instruction review issues", exception);
        }
    }

    private String writeIssues(List<InstructionLintIssueDto> issues) {
        try {
            return objectMapper.writeValueAsString(issues == null ? List.of() : issues);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize instruction review issues", exception);
        }
    }

    private void logDecision(UUID activityId, InstructionReviewSnapshotDto snapshot, long startedAtNanos) {
        LOGGER.info(
                "Instruction review decision: activityId={} reviewHash={} reviewStatus={} qualityStatus={} canSave={} modelCalled={} fromCache={} promptVersion={} modelName={} durationMs={} issuesCount={}",
                activityId,
                snapshot.reviewHash(),
                snapshot.reviewStatus(),
                snapshot.qualityStatus(),
                snapshot.canSave(),
                snapshot.modelCalled(),
                snapshot.fromCache(),
                instructionReviewService.promptVersion(),
                instructionReviewService.currentModelName(),
                (System.nanoTime() - startedAtNanos) / 1_000_000,
                snapshot.issues() == null ? 0 : snapshot.issues().size());
    }

    public record ReviewBeforeSaveDecision(
            InstructionReviewSnapshotDto snapshot,
            InstructionReviewResult reviewResult,
            boolean canSave) {
    }
}
