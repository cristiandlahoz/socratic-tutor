package com.wornux.services.training_activity.instruction_review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.wornux.services.training_activity.TrainingTutorJobService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.test.util.ReflectionTestUtils;

class InstructionReviewJobWorkerTest {

    @Test
    void br22_rejectsALeaseThatCannotCoverDeadlineAndResultApplication() {
        var workerExecutor = executor();
        var modelExecutor = executor();
        try {
            assertThatThrownBy(() -> new InstructionReviewJobWorker(mock(AdvisoryInstructionReviewService.class),
                    mock(InstructionReviewService.class), mock(TrainingTutorJobService.class), workerExecutor, modelExecutor,
                    15_000, 30, 15_000, 15, openAiProperties(Duration.ofSeconds(5), 0), new OpenAiChatProperties(), new SimpleMeterRegistry()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Tutor job lease must exceed the model deadline and result-application window.");
        }
        finally {
            workerExecutor.shutdownNow();
            modelExecutor.shutdownNow();
        }
    }

    @Test
    void br22_rejectsAnEffectiveOpenAiTimeoutAtOrAfterTutorDeadline() {
        var workerExecutor = executor();
        var modelExecutor = executor();
        try {
            var chatProperties = new OpenAiChatProperties();
            chatProperties.setTimeout(Duration.ofSeconds(15));

            assertThatThrownBy(() -> new InstructionReviewJobWorker(mock(AdvisoryInstructionReviewService.class),
                    mock(InstructionReviewService.class), mock(TrainingTutorJobService.class), workerExecutor, modelExecutor,
                    15_000, 30, 15_000, 30, openAiProperties(Duration.ofSeconds(5), 0), chatProperties, new SimpleMeterRegistry()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("OpenAI request timeout must be positive and strictly below the tutor model deadline.");
        }
        finally {
            workerExecutor.shutdownNow();
            modelExecutor.shutdownNow();
        }
    }

    @Test
    void br22_rejectsOpenAiClientRetriesThatCouldOutrunTheTutorDeadline() {
        var workerExecutor = executor();
        var modelExecutor = executor();
        try {
            assertThatThrownBy(() -> new InstructionReviewJobWorker(mock(AdvisoryInstructionReviewService.class),
                    mock(InstructionReviewService.class), mock(TrainingTutorJobService.class), workerExecutor, modelExecutor,
                    15_000, 30, 15_000, 30, openAiProperties(Duration.ofSeconds(5), 1), new OpenAiChatProperties(), new SimpleMeterRegistry()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("OpenAI client retries must be disabled so the request timeout bounds shared executor occupancy.");
        }
        finally {
            workerExecutor.shutdownNow();
            modelExecutor.shutdownNow();
        }
    }

    @Test
    void tutorTimeoutIncrementsOnlyTutorTimeoutTelemetry() {
        var workerExecutor = executor();
        var modelExecutor = executor();
        var meterRegistry = new SimpleMeterRegistry();
        try {
            var tutorJobService = mock(TrainingTutorJobService.class);
            var jobId = UUID.randomUUID();
            var work = new TrainingTutorJobService.TutorWork(
                    jobId, null, null, null, 0, 1, "", List.of());
            when(tutorJobService.claim(eq(jobId), any(), any())).thenReturn(work);
            when(tutorJobService.callModel(work)).thenAnswer(_ -> {
                Thread.sleep(100);
                return null;
            });
            when(tutorJobService.applyFailure(jobId, 1, "MODEL_TIMEOUT"))
                    .thenReturn(new TrainingTutorJobService.TutorFailureOutcome(true, false, false));
            var worker = new InstructionReviewJobWorker(mock(AdvisoryInstructionReviewService.class),
                    mock(InstructionReviewService.class), tutorJobService, workerExecutor, modelExecutor,
                    15_000, 30, 10, 6, openAiProperties(Duration.ofMillis(5), 0), new OpenAiChatProperties(), meterRegistry);

            ReflectionTestUtils.invokeMethod(worker, "processTutor", jobId);

            assertThat(meterRegistry.get("training.activity.tutor.timeout").counter().count()).isEqualTo(1);
            assertThat(meterRegistry.get("training.activity.instruction-review.timeout").counter().count()).isZero();
            verify(tutorJobService).applyFailure(jobId, 1, "MODEL_TIMEOUT");
        }
        finally {
            workerExecutor.shutdownNow();
            modelExecutor.shutdownNow();
        }
    }

    @Test
    void tutorInterruptionCancelsTheSubmittedModelFutureAndPreservesTheInterrupt() throws InterruptedException {
        var workerExecutor = executor();
        var modelExecutor = executor();
        var meterRegistry = new SimpleMeterRegistry();
        var modelStarted = new CountDownLatch(1);
        var modelInterrupted = new CountDownLatch(1);
        var workerInterrupted = new AtomicBoolean();
        try {
            var tutorJobService = mock(TrainingTutorJobService.class);
            var jobId = UUID.randomUUID();
            var work = new TrainingTutorJobService.TutorWork(jobId, null, null, null, 0, 1, "", List.of());
            when(tutorJobService.claim(eq(jobId), any(), any())).thenReturn(work);
            when(tutorJobService.callModel(work)).thenAnswer(_ -> awaitCancellation(modelStarted, modelInterrupted));
            when(tutorJobService.applyFailure(jobId, 1, "MODEL_UNAVAILABLE"))
                    .thenReturn(new TrainingTutorJobService.TutorFailureOutcome(true, false, false));
            var worker = worker(tutorJobService, workerExecutor, modelExecutor, meterRegistry);

            var processingThread = startInterruptedWorker(
                    () -> ReflectionTestUtils.invokeMethod(worker, "processTutor", jobId), workerInterrupted);
            assertThat(modelStarted.await(1, TimeUnit.SECONDS)).isTrue();
            processingThread.interrupt();
            processingThread.join(1_000);

            assertThat(processingThread.isAlive()).isFalse();
            assertThat(modelInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(workerInterrupted.get()).isTrue();
            verify(tutorJobService).applyFailure(jobId, 1, "MODEL_UNAVAILABLE");
        }
        finally {
            workerExecutor.shutdownNow();
            modelExecutor.shutdownNow();
        }
    }

    @Test
    void finalReportInterruptionCancelsTheSubmittedModelFutureAndPreservesTheInterrupt() throws InterruptedException {
        var workerExecutor = executor();
        var modelExecutor = executor();
        var meterRegistry = new SimpleMeterRegistry();
        var modelStarted = new CountDownLatch(1);
        var modelInterrupted = new CountDownLatch(1);
        var workerInterrupted = new AtomicBoolean();
        try {
            var tutorJobService = mock(TrainingTutorJobService.class);
            var jobId = UUID.randomUUID();
            var work = new TrainingTutorJobService.FinalReportWork(jobId, 1, 1, null, null, List.of());
            when(tutorJobService.claimFinalReport(eq(jobId), any(), any())).thenReturn(work);
            when(tutorJobService.callFinalReportModel(work)).thenAnswer(_ -> awaitCancellation(modelStarted, modelInterrupted));
            when(tutorJobService.applyFinalReportFailure(jobId, 1, "MODEL_UNAVAILABLE"))
                    .thenReturn(new TrainingTutorJobService.TutorFailureOutcome(true, false, false));
            var worker = worker(tutorJobService, workerExecutor, modelExecutor, meterRegistry);

            var processingThread = startInterruptedWorker(
                    () -> ReflectionTestUtils.invokeMethod(worker, "processFinalReport", jobId), workerInterrupted);
            assertThat(modelStarted.await(1, TimeUnit.SECONDS)).isTrue();
            processingThread.interrupt();
            processingThread.join(1_000);

            assertThat(processingThread.isAlive()).isFalse();
            assertThat(modelInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(workerInterrupted.get()).isTrue();
            verify(tutorJobService).applyFinalReportFailure(jobId, 1, "MODEL_UNAVAILABLE");
        }
        finally {
            workerExecutor.shutdownNow();
            modelExecutor.shutdownNow();
        }
    }

    private static InstructionReviewJobWorker worker(
            TrainingTutorJobService tutorJobService,
            ThreadPoolExecutor workerExecutor,
            ThreadPoolExecutor modelExecutor,
            SimpleMeterRegistry meterRegistry) {
        return new InstructionReviewJobWorker(mock(AdvisoryInstructionReviewService.class), mock(InstructionReviewService.class),
                tutorJobService, workerExecutor, modelExecutor, 15_000, 30, 15_000, 30,
                openAiProperties(Duration.ofSeconds(5), 0), new OpenAiChatProperties(), meterRegistry);
    }

    private static Object awaitCancellation(CountDownLatch modelStarted, CountDownLatch modelInterrupted) throws InterruptedException {
        modelStarted.countDown();
        try {
            Thread.sleep(10_000);
        }
        catch (InterruptedException exception) {
            modelInterrupted.countDown();
            throw exception;
        }
        return null;
    }

    private static Thread startInterruptedWorker(Runnable action, AtomicBoolean interrupted) {
        var thread = Thread.ofPlatform().start(() -> {
            action.run();
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        return thread;
    }

    private static ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    private static OpenAiCommonProperties openAiProperties(Duration timeout, int maxRetries) {
        var properties = new OpenAiCommonProperties();
        properties.setTimeout(timeout);
        properties.setMaxRetries(maxRetries);
        return properties;
    }
}
