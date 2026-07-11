package com.wornux.services.training_activity.instruction_review;

import java.time.Instant;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InstructionReviewJobWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstructionReviewJobWorker.class);

    private final AdvisoryInstructionReviewService reviewService;
    private final InstructionReviewService reviewEngine;
    private final ThreadPoolExecutor workerExecutor;
    private final ThreadPoolExecutor modelExecutor;
    private final long deadlineMs;
    private final long leaseSeconds;
    private final Counter errorCounter;
    private final Counter retryCounter;
    private final Counter timeoutCounter;
    private final Counter saturationCounter;
    private final Timer modelLatencyTimer;

    public InstructionReviewJobWorker(AdvisoryInstructionReviewService reviewService, InstructionReviewService reviewEngine,
            @Qualifier("instructionReviewWorkerExecutor") ThreadPoolExecutor workerExecutor,
            @Qualifier("instructionReviewModelExecutor") ThreadPoolExecutor modelExecutor,
            @Value("${app.ai.instruction-review.deadline-ms:15000}") long deadlineMs,
            @Value("${app.ai.instruction-review.lease-seconds:30}") long leaseSeconds,
            MeterRegistry meterRegistry) {
        this.reviewService = reviewService; this.reviewEngine = reviewEngine; this.workerExecutor = workerExecutor;
        this.modelExecutor = modelExecutor; this.deadlineMs = deadlineMs; this.leaseSeconds = leaseSeconds;
        this.errorCounter = meterRegistry.counter("training.activity.instruction-review.error");
        this.retryCounter = meterRegistry.counter("training.activity.instruction-review.retry");
        this.timeoutCounter = meterRegistry.counter("training.activity.instruction-review.timeout");
        this.saturationCounter = meterRegistry.counter("training.activity.instruction-review.saturation");
        this.modelLatencyTimer = meterRegistry.timer("training.activity.instruction-review.model.latency");
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
        reviewService.availableJobIds(Instant.now(), capacity).forEach(id -> {
            try {
                workerExecutor.execute(() -> process(id));
            }
            catch (RejectedExecutionException exception) {
                saturationCounter.increment();
                LOGGER.warn("instructionReview worker submission rejected: jobId={}", id);
            }
        });
    }

    private void process(java.util.UUID jobId) {
        var work = reviewService.claim(jobId, Instant.now(), Instant.now().plusSeconds(leaseSeconds));
        if (work == null) return;
        Future<InstructionReviewResult> modelFuture = null;
        var latencySample = Timer.start();
        try {
            modelFuture = modelExecutor.submit(() -> reviewEngine.review(work.title(), work.instructions()));
            var result = modelFuture.get(deadlineMs, TimeUnit.MILLISECONDS);
            reviewService.applySuccess(work.jobId(), result);
        }
        catch (TimeoutException exception) {
            if (modelFuture != null) {
                modelFuture.cancel(true);
            }
            LOGGER.warn("instructionReview model call timed out and was cancelled: jobId={} deadlineMs={}",
                    work.jobId(), deadlineMs);
            timeoutCounter.increment();
            recordFailure(work.jobId(), "MODEL_TIMEOUT");
        }
        catch (RejectedExecutionException exception) {
            saturationCounter.increment();
            recordFailure(work.jobId(), "MODEL_UNAVAILABLE");
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(work.jobId(), "MODEL_UNAVAILABLE");
        }
        catch (Exception exception) {
            recordFailure(work.jobId(), "MODEL_UNAVAILABLE");
        }
        finally {
            latencySample.stop(modelLatencyTimer);
        }
    }

    private void recordFailure(java.util.UUID jobId, String failureCode) {
        errorCounter.increment();
        if (reviewService.applyFailure(jobId, failureCode)) {
            retryCounter.increment();
        }
    }

    private int workerCapacity() {
        var immediatelyRunnable = Math.max(0, workerExecutor.getMaximumPoolSize() - workerExecutor.getActiveCount());
        return Math.min(8, immediatelyRunnable + workerExecutor.getQueue().remainingCapacity());
    }
}
