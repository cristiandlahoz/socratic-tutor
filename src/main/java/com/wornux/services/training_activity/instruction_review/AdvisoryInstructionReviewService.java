package com.wornux.services.training_activity.instruction_review;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAiJob;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobType;
import com.wornux.data.entities.training_activity.instruction_review.InstructionReviewStatus;
import com.wornux.data.repositories.training_activity.TrainingActivityAiJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvisoryInstructionReviewService {
    private static final int REVIEW_PRIORITY = 100;
    private static final int MAX_ATTEMPTS = 3;

    private final TrainingActivityAiJobRepository jobRepository;
    private final InstructionReviewService reviewEngine;
    private final Cache<ReviewKey, InstructionReviewSnapshotDto> reviews;

    @Autowired
    public AdvisoryInstructionReviewService(
            TrainingActivityAiJobRepository jobRepository,
            InstructionReviewService reviewEngine) {
        this(jobRepository, reviewEngine, Ticker.systemTicker());
    }

    AdvisoryInstructionReviewService(TrainingActivityAiJobRepository jobRepository,
            InstructionReviewService reviewEngine, Ticker ticker) {
        this.jobRepository = jobRepository;
        this.reviewEngine = reviewEngine;
        this.reviews = Caffeine.newBuilder().maximumSize(1_000)
                .expireAfterAccess(30, TimeUnit.MINUTES).ticker(ticker).build();
    }

    @Transactional
    public InstructionReviewSnapshotDto request(UUID professorId, String title, String instructions) {
        requireText(title, "Title");
        requireText(instructions, "Instructions");
        var key = key(professorId, title, instructions);
        var cached = reviews.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        var reviewing = snapshot(key, InstructionReviewStatus.REVIEWING, null, false, "Revisando instrucciones…",
                List.of(), "", Instant.now());
        reviews.put(key, reviewing);
        if (jobRepository.findFirstBySemanticKeyAndStatusInOrderByCreatedAtDesc(
                key.semanticKey(), List.of(TrainingActivityAiJobStatus.PENDING, TrainingActivityAiJobStatus.RETRYABLE,
                        TrainingActivityAiJobStatus.RUNNING)).isEmpty()) {
            var now = Instant.now();
            var job = new TrainingActivityAiJob();
            job.setId(UUID.randomUUID());
            job.setJobType(TrainingActivityAiJobType.INSTRUCTION_REVIEW);
            job.setPriority(REVIEW_PRIORITY);
            job.setReviewProfessorId(professorId);
            job.setReviewTitle(normalize(title));
            job.setReviewInstructions(normalize(instructions));
            job.setSemanticKey(key.semanticKey());
            job.setGeneration(jobRepository.findTopBySemanticKeyOrderByGenerationDesc(key.semanticKey())
                    .map(previous -> previous.getGeneration() + 1).orElse(0));
            job.setStatus(TrainingActivityAiJobStatus.PENDING);
            job.setMaxAttempts(MAX_ATTEMPTS);
            job.setAvailableAt(now);
            job.setCreatedAt(now);
            job.setUpdatedAt(now);
            jobRepository.save(job);
        }
        return reviewing;
    }

    public InstructionReviewSnapshotDto current(UUID professorId, String title, String instructions) {
        var key = key(professorId, title, instructions);
        return reviews.getIfPresent(key);
    }

    public ReviewWork work(TrainingActivityAiJob job) {
        return new ReviewWork(job.getId(), job.getGeneration(), key(
                job.getReviewProfessorId(), job.getReviewTitle(), job.getReviewInstructions()),
                job.getReviewTitle(), job.getReviewInstructions());
    }

    public ReviewWork work(UUID jobId) {
        return work(jobRepository.findById(jobId).orElseThrow());
    }

    @Transactional
    public boolean applySuccess(ReviewWork work, InstructionReviewResult result) {
        var now = Instant.now();
        if (jobRepository.fenceSuccess(work.jobId(), TrainingActivityAiJobType.INSTRUCTION_REVIEW,
                TrainingActivityAiJobStatus.RUNNING, TrainingActivityAiJobStatus.SUCCEEDED,
                work.ownershipGeneration(), now) == 0) {
            return false;
        }
        reviews.put(work.key(), snapshot(work.key(),
                result.validInstruction() ? InstructionReviewStatus.COMPLETED : InstructionReviewStatus.LOCAL_INVALID,
                result.qualityStatus(), result.isGood(), result.summary(), issues(result.issues()),
                result.improvedInstructions(), result.reviewedAt()));
        cacheExactAcceptedSuggestion(work, result);
        return true;
    }

    @Transactional
    public boolean applyFailure(ReviewWork work, String failureCode) {
        var job = jobRepository.findById(work.jobId()).orElseThrow();
        var retry = job.getAttemptCount() < job.getMaxAttempts();
        var now = Instant.now();
        if (jobRepository.fenceFailure(work.jobId(), TrainingActivityAiJobType.INSTRUCTION_REVIEW,
                TrainingActivityAiJobStatus.RUNNING,
                retry ? TrainingActivityAiJobStatus.RETRYABLE : TrainingActivityAiJobStatus.FAILED,
                work.ownershipGeneration(), now.plusSeconds(retry ? Math.max(5, job.getAttemptCount() * 5L) : 0),
                failureCode, job.getInputVersion(), now) == 0) {
            return false;
        }
        if (!retry) {
            reviews.put(work.key(), snapshot(work.key(), InstructionReviewStatus.UNAVAILABLE, null, false,
                    "La revisión de IA no está disponible.", List.of(), "", now));
        }
        return retry;
    }

    private InstructionReviewSnapshotDto snapshot(ReviewKey key, InstructionReviewStatus status,
            InstructionQualityStatus quality, boolean canSave, String message, List<InstructionLintIssueDto> issues,
            String recreated, Instant reviewedAt) {
        return new InstructionReviewSnapshotDto(null, key.hash(), status, quality, canSave, message, false,
                issues, recreated == null ? "" : recreated, reviewedAt);
    }

    private List<InstructionLintIssueDto> issues(List<InstructionReviewIssue> values) {
        return (values == null ? List.<InstructionReviewIssue>of() : values).stream()
                .filter(issue -> issue != null && issue.severity() != null)
                .map(issue -> new InstructionLintIssueDto(issue.id(), issue.category(), issue.severity().name(),
                        issue.startOffset(), issue.endOffset(), issue.message(), issue.whyItMatters(),
                        issue.suggestedReplacement(), issue.suggestionReason()))
                .toList();
    }

    private void cacheExactAcceptedSuggestion(ReviewWork work, InstructionReviewResult result) {
        var actionableIssues = (result.issues() == null ? List.<InstructionReviewIssue>of() : result.issues()).stream()
                .filter(issue -> issue != null
                        && issue.startOffset() != null
                        && issue.endOffset() != null
                        && issue.suggestedReplacement() != null
                        && !issue.suggestedReplacement().isBlank())
                .toList();
        if (actionableIssues.size() != 1) {
            return;
        }
        var acceptedInstructions = applySuggestion(work.instructions(), actionableIssues.getFirst());
        if (acceptedInstructions == null || acceptedInstructions.isBlank()) {
            return;
        }
        var acceptedKey = key(work.key().professorId(), work.title(), acceptedInstructions);
        reviews.put(acceptedKey, snapshot(
                acceptedKey,
                InstructionReviewStatus.COMPLETED,
                InstructionQualityStatus.GOOD,
                true,
                "Sugerencia aplicada; no es necesario revisar nuevamente.",
                List.of(),
                acceptedInstructions,
                result.reviewedAt()));
    }

    private String applySuggestion(String instructions, InstructionReviewIssue issue) {
        var source = normalize(instructions);
        var start = issue.startOffset();
        var end = issue.endOffset();
        if (start < 0 || end <= start || end > source.length()) {
            return null;
        }
        var replacement = issue.suggestedReplacement().trim();
        var normalizedSource = normalizeForSuggestionComparison(source);
        var normalizedReplacement = normalizeForSuggestionComparison(replacement);
        var prefix = normalizeForSuggestionComparison(source.substring(0, start));
        var wholeReplacement = normalizedReplacement.startsWith(normalizedSource)
                || (prefix.length() >= 12 && normalizedReplacement.startsWith(prefix));
        return normalize(wholeReplacement
                ? replacement
                : source.substring(0, start) + replacement + source.substring(end));
    }

    private static String normalizeForSuggestionComparison(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private ReviewKey key(UUID professorId, String title, String instructions) {
        var normalized = professorId + "\n" + normalize(title).toLowerCase(Locale.ROOT) + "\n" + normalize(instructions);
        var hash = reviewEngine.hashInstructions(normalized);
        return new ReviewKey(professorId, hash, "instruction-review:" + hash);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace("\r\n", "\n");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
    }

    public record ReviewKey(UUID professorId, String hash, String semanticKey) {}
    public record ReviewWork(UUID jobId, int ownershipGeneration, ReviewKey key, String title, String instructions) {}
}
