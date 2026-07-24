package com.wornux.services.training_activity;

import java.time.Instant;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.wornux.data.entities.training_activity.TrainingActivityAiJobType;
import com.wornux.services.training_activity.instruction_review.AdvisoryInstructionReviewService;
import com.wornux.services.training_activity.instruction_review.InstructionReviewModelOutputException;
import com.wornux.services.training_activity.instruction_review.InstructionReviewResult;
import com.wornux.services.training_activity.instruction_review.InstructionReviewService;
import com.wornux.services.training_activity.instruction_review.InstructionReviewUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfigurationUtil;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;

@Component
public class TrainingActivityAiJobWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrainingActivityAiJobWorker.class);
    private static final long RESULT_APPLICATION_WINDOW_MS = 5_000;
    private static final int MAX_ASYNC_WRAPPER_DEPTH = 4;

    private final AdvisoryInstructionReviewService reviewService;
    private final InstructionReviewService reviewEngine;
    private final TrainingTutorJobService tutorJobService;
    private final ThreadPoolExecutor workerExecutor;
    private final ThreadPoolExecutor modelExecutor;
    private final long deadlineMs;
    private final long leaseSeconds;
    private final long tutorDeadlineMs;
    private final long tutorLeaseSeconds;
    private final Counter errorCounter;
    private final Counter retryCounter;
    private final Counter timeoutCounter;
    private final Counter saturationCounter;
    private final Timer modelLatencyTimer;
    private final Counter tutorErrorCounter;
    private final Counter tutorRetryCounter;
    private final Counter tutorTerminalCounter;
    private final Counter tutorTimeoutCounter;
    private final Counter tutorStaleCounter;
    private final Timer tutorModelLatencyTimer;
    private final Counter reportErrorCounter;
    private final Counter reportRetryCounter;
    private final Counter reportTerminalCounter;
    private final Counter reportStaleCounter;
    private final Timer reportModelLatencyTimer;

    public TrainingActivityAiJobWorker(AdvisoryInstructionReviewService reviewService, InstructionReviewService reviewEngine,
            TrainingTutorJobService tutorJobService,
            @Qualifier("instructionReviewWorkerExecutor") ThreadPoolExecutor workerExecutor,
            @Qualifier("instructionReviewModelExecutor") ThreadPoolExecutor modelExecutor,
            @Value("${app.ai.instruction-review.deadline-ms:15000}") long deadlineMs,
             @Value("${app.ai.instruction-review.lease-seconds:30}") long leaseSeconds,
             @Value("${app.ai.adaptive-tutor.deadline-ms:60000}") long tutorDeadlineMs,
             @Value("${app.ai.adaptive-tutor.lease-seconds:75}") long tutorLeaseSeconds,
            OpenAiCommonProperties openAiProperties,
            OpenAiChatProperties openAiChatProperties,
            MeterRegistry meterRegistry) {
        this.reviewService = reviewService; this.reviewEngine = reviewEngine; this.tutorJobService = tutorJobService; this.workerExecutor = workerExecutor;
        this.modelExecutor = modelExecutor; this.deadlineMs = deadlineMs; this.leaseSeconds = leaseSeconds;
        this.tutorDeadlineMs = tutorDeadlineMs; this.tutorLeaseSeconds = tutorLeaseSeconds;
        var openAiRequestProperties = OpenAiAutoConfigurationUtil.resolveCommonProperties(openAiProperties, openAiChatProperties);
        if (openAiRequestProperties.getTimeout().isZero() || openAiRequestProperties.getTimeout().isNegative()
                || openAiRequestProperties.getTimeout().compareTo(java.time.Duration.ofMillis(tutorDeadlineMs)) >= 0) {
            throw new IllegalArgumentException("OpenAI request timeout must be positive and strictly below the tutor model deadline.");
        }
        if (openAiRequestProperties.getMaxRetries() != 0) {
            throw new IllegalArgumentException("OpenAI client retries must be disabled so the request timeout bounds shared executor occupancy.");
        }
        if (tutorLeaseSeconds * 1_000L <= tutorDeadlineMs + RESULT_APPLICATION_WINDOW_MS) {
            throw new IllegalArgumentException("Tutor job lease must exceed the model deadline and result-application window.");
        }
        this.errorCounter = meterRegistry.counter("training.activity.instruction-review.error");
        this.retryCounter = meterRegistry.counter("training.activity.instruction-review.retry");
        this.timeoutCounter = meterRegistry.counter("training.activity.instruction-review.timeout");
        this.saturationCounter = meterRegistry.counter("training.activity.instruction-review.saturation");
        this.modelLatencyTimer = meterRegistry.timer("training.activity.instruction-review.model.latency");
        this.tutorErrorCounter = meterRegistry.counter("training.activity.tutor.error");
        this.tutorRetryCounter = meterRegistry.counter("training.activity.tutor.retry");
        this.tutorTerminalCounter = meterRegistry.counter("training.activity.tutor.terminal");
        this.tutorTimeoutCounter = meterRegistry.counter("training.activity.tutor.timeout");
        this.tutorStaleCounter = meterRegistry.counter("training.activity.tutor.stale");
        this.tutorModelLatencyTimer = meterRegistry.timer("training.activity.tutor.model.latency");
        this.reportErrorCounter = meterRegistry.counter("training.activity.report.error");
        this.reportRetryCounter = meterRegistry.counter("training.activity.report.retry");
        this.reportTerminalCounter = meterRegistry.counter("training.activity.report.terminal");
        this.reportStaleCounter = meterRegistry.counter("training.activity.report.stale");
        this.reportModelLatencyTimer = meterRegistry.timer("training.activity.report.model.latency");
        Gauge.builder("training.activity.instruction-review.worker.queue.depth", workerExecutor.getQueue(), java.util.Queue::size)
                .register(meterRegistry);
        Gauge.builder("training.activity.instruction-review.model.queue.depth", modelExecutor.getQueue(), java.util.Queue::size)
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.ai.instruction-review.poll-ms:1000}")
    public void poll() {
        var capacity = workerCapacity();
        if (capacity == 0) {
            saturationCounter.increment();
            LOGGER.warn("instructionReview worker queue is saturated: active={} queued={}",
                    workerExecutor.getActiveCount(), workerExecutor.getQueue().size());
            return;
        }
        for (var index = 0; index < capacity; index++) {
            var claimed = tutorJobService.claimNextWork(Instant.now(), Instant.now().plusSeconds(tutorLeaseSeconds));
            if (claimed == null) {
                continue;
            }
            var job = claimed.job();
            if (job.getJobType() == TrainingActivityAiJobType.INSTRUCTION_REVIEW) {
                submitReview(job.getId());
            }
            else if (job.getJobType() == TrainingActivityAiJobType.FINAL_REPORT) {
                submitFinalReport(claimed.finalReportWork());
            }
            else {
                submitTutor(claimed.tutorWork());
            }
        }
    }

    private void submitReview(java.util.UUID id) {
            try {
                workerExecutor.execute(() -> process(id));
            }
            catch (RejectedExecutionException exception) {
                saturationCounter.increment();
                LOGGER.warn("instructionReview worker submission rejected: jobId={}", id);
            }
    }

    private void submitTutor(TrainingTutorJobService.TutorWork work) {
        try {
            workerExecutor.execute(() -> processTutor(work));
        }
        catch (RejectedExecutionException exception) {
            saturationCounter.increment();
            LOGGER.warn("tutor worker submission rejected: jobId={}", work.jobId());
        }
    }

    private void submitFinalReport(TrainingTutorJobService.FinalReportWork work) {
        try {
            workerExecutor.execute(() -> processFinalReport(work));
        }
        catch (RejectedExecutionException exception) {
            saturationCounter.increment();
            LOGGER.warn("finalReport worker submission rejected: jobId={}", work.jobId());
        }
    }

    private void process(java.util.UUID jobId) {
        var work = reviewService.work(jobId);
        Future<InstructionReviewResult> modelFuture = null;
        var latencySample = Timer.start();
        try {
            modelFuture = modelExecutor.submit(() -> reviewEngine.review(work.title(), work.instructions()));
            var result = modelFuture.get(deadlineMs, TimeUnit.MILLISECONDS);
            reviewService.applySuccess(work, result);
        }
        catch (TimeoutException exception) {
            if (modelFuture != null) {
                modelFuture.cancel(true);
            }
            LOGGER.warn("instructionReview model call timed out and was cancelled: jobId={} deadlineMs={}",
                    work.jobId(), deadlineMs);
            timeoutCounter.increment();
            recordFailure(work, "MODEL_TIMEOUT");
        }
        catch (RejectedExecutionException exception) {
            saturationCounter.increment();
            recordFailure(work, "MODEL_UNAVAILABLE");
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(work, "MODEL_UNAVAILABLE");
        }
        catch (ExecutionException exception) {
            var failureCode = reviewFailureCode(exception);
            LOGGER.warn("instructionReview model call failed: jobId={} failureCode={}", work.jobId(), failureCode);
            recordFailure(work, failureCode);
        }
        catch (Exception exception) {
            recordFailure(work, "MODEL_UNAVAILABLE");
        }
        finally {
            latencySample.stop(modelLatencyTimer);
        }
    }

    private void processTutor(TrainingTutorJobService.TutorWork work) {
        Future<com.wornux.services.training_activity.AdaptiveTutorDecision> modelFuture = null;
        var latencySample = Timer.start();
        try {
            modelFuture = modelExecutor.submit(() -> tutorJobService.callModel(work));
            if (!tutorJobService.applySuccess(work.jobId(), work.ownershipGeneration(),
                    modelFuture.get(tutorDeadlineMs, TimeUnit.MILLISECONDS))) {
                tutorStaleCounter.increment();
            }
        }
        catch (TimeoutException exception) {
            if (modelFuture != null) modelFuture.cancel(true);
            tutorTimeoutCounter.increment();
            recordTutorFailure(work, "MODEL_TIMEOUT");
        }
        catch (RejectedExecutionException | InterruptedException exception) {
            if (modelFuture != null) modelFuture.cancel(true);
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            saturationCounter.increment();
            recordTutorFailure(work, "MODEL_UNAVAILABLE");
        }
        catch (Exception exception) {
            recordTutorFailure(work, tutorFailureCode(exception));
        }
        finally {
            latencySample.stop(tutorModelLatencyTimer);
        }
    }

    private void recordTutorFailure(TrainingTutorJobService.TutorWork work, String failureCode) {
        tutorErrorCounter.increment();
        var outcome = tutorJobService.applyFailure(work.jobId(), work.ownershipGeneration(), failureCode);
        LOGGER.warn("adaptiveTutor.job_failure jobId={} assignmentId={} failureCode={} retryScheduled={} terminal={} stale={}",
                work.jobId(), work.assignment().getId(), failureCode, outcome.retryScheduled(), outcome.terminal(), outcome.stale());
        if (outcome.retryScheduled()) {
            tutorRetryCounter.increment();
        }
        else if (outcome.terminal()) {
            tutorTerminalCounter.increment();
        }
        else if (outcome.stale()) {
            tutorStaleCounter.increment();
        }
    }

    private void processFinalReport(TrainingTutorJobService.FinalReportWork work) {
        Future<FinalReportCandidate> modelFuture = null;
        var latencySample = Timer.start();
        try {
            modelFuture = modelExecutor.submit(() -> tutorJobService.callFinalReportModel(work));
            if (!tutorJobService.applyFinalReportSuccess(work.jobId(), work.ownershipGeneration(),
                    modelFuture.get(tutorDeadlineMs, TimeUnit.MILLISECONDS))) {
                reportStaleCounter.increment();
            }
        }
        catch (TimeoutException exception) {
            if (modelFuture != null) {
                modelFuture.cancel(true);
            }
            recordFinalReportFailure(work, "MODEL_TIMEOUT", exception);
        }
        catch (RejectedExecutionException | InterruptedException exception) {
            if (modelFuture != null) {
                modelFuture.cancel(true);
            }
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            saturationCounter.increment();
            recordFinalReportFailure(work, "MODEL_UNAVAILABLE", exception);
        }
        catch (Exception exception) {
            recordFinalReportFailure(work, finalReportFailureCode(exception), exception);
        }
        finally {
            latencySample.stop(reportModelLatencyTimer);
        }
    }

    private void recordFinalReportFailure(TrainingTutorJobService.FinalReportWork work, String failureCode, Throwable exception) {
        reportErrorCounter.increment();
        var outcome = tutorJobService.applyFinalReportFailure(work.jobId(), work.ownershipGeneration(), failureCode);
        var cause = unwrapAsyncFailure(exception);
        LOGGER.warn("adaptiveTutor.final_report_failure jobId={} assignmentId={} failureCode={} retryScheduled={} terminal={} stale={} rootExceptionClass={}",
                work.jobId(), work.assignment() == null ? null : work.assignment().getId(), failureCode,
                outcome.retryScheduled(), outcome.terminal(), outcome.stale(),
                cause == null ? null : cause.getClass().getName());
        if (outcome.retryScheduled()) {
            reportRetryCounter.increment();
        }
        else if (outcome.terminal()) {
            reportTerminalCounter.increment();
        }
        else if (outcome.stale()) {
            reportStaleCounter.increment();
        }
    }

    private String finalReportFailureCode(Throwable exception) {
        var cause = unwrapAsyncFailure(exception);
        if (cause instanceof FinalReportCandidateValidationException validationException) {
            return "INVALID_FINAL_REPORT_EVIDENCE_" + validationException.reason().name();
        }
        if (cause instanceof FinalReportAuthorityException) {
            return FinalReportAuthorityException.FAILURE_CODE;
        }
        if (cause instanceof AdaptiveTutorModelOutputException modelOutputException
                && modelOutputException.failureCode() != null && !modelOutputException.failureCode().isBlank()) {
            return modelOutputException.failureCode();
        }
        return "MODEL_INVALID_OR_UNAVAILABLE";
    }

    private void recordFailure(AdvisoryInstructionReviewService.ReviewWork work, String failureCode) {
        errorCounter.increment();
        if (reviewService.applyFailure(work, failureCode)) {
            retryCounter.increment();
        }
    }

    private String reviewFailureCode(ExecutionException exception) {
        var cause = unwrapAsyncFailure(exception);
        if (cause instanceof InstructionReviewModelOutputException modelOutputException) {
            return failureCode(modelOutputException, "MODEL_OUTPUT_INVALID");
        }
        if (cause instanceof InstructionReviewUnavailableException unavailableException) {
            return failureCode(unavailableException, "MODEL_UNAVAILABLE");
        }
        return "MODEL_UNAVAILABLE";
    }

    private String tutorFailureCode(Throwable exception) {
        var cause = unwrapAsyncFailure(exception);
        if (cause instanceof AdaptiveTutorModelOutputException modelOutputException) {
            return modelOutputException.failureCode();
        }
        if (containsPersistenceConstraintFailure(cause)) {
            return "PERSISTENCE_CONSTRAINT_RETRY";
        }
        return "MODEL_UNAVAILABLE";
    }

    private boolean containsPersistenceConstraintFailure(Throwable exception) {
        var current = exception;
        for (var depth = 0; current != null && depth < MAX_ASYNC_WRAPPER_DEPTH * 2; depth++) {
            if (current instanceof DataIntegrityViolationException
                    || current instanceof org.hibernate.exception.ConstraintViolationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Throwable unwrapAsyncFailure(Throwable exception) {
        var current = exception;
        for (var depth = 0; depth < MAX_ASYNC_WRAPPER_DEPTH
                && (current instanceof ExecutionException || current instanceof CompletionException); depth++) {
            var cause = current.getCause();
            if (cause == null) {
                return current;
            }
            current = cause;
        }
        return current;
    }

    private String failureCode(InstructionReviewUnavailableException exception, String fallback) {
        var result = exception.getReviewResult();
        return result == null || result.executionStatus() == null ? fallback : result.executionStatus().name();
    }

    private int workerCapacity() {
        var immediatelyRunnable = Math.max(0, workerExecutor.getMaximumPoolSize() - workerExecutor.getActiveCount());
        return Math.min(8, immediatelyRunnable + workerExecutor.getQueue().remainingCapacity());
    }
}
