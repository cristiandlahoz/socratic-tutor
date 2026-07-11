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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
