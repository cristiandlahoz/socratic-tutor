package com.wornux.services.training_activity.instruction_review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.wornux.data.entities.training_activity.InstructionQualityStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAiJob;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobType;
import com.wornux.data.repositories.training_activity.TrainingActivityAiJobRepository;
import org.junit.jupiter.api.Test;

class AdvisoryInstructionReviewServiceTest {
    @Test
    void cacheKeyIncludesProfessorTitleAndInstructionsAndAvoidsDuplicateJobs() {
        var jobs = mock(TrainingActivityAiJobRepository.class);
        var engine = mock(InstructionReviewService.class);
        when(engine.hashInstructions(org.mockito.ArgumentMatchers.anyString())).thenReturn("review-hash");
        when(jobs.findFirstBySemanticKeyAndStatusInOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Optional.empty());
        var service = new AdvisoryInstructionReviewService(jobs, engine);

        var professor = UUID.randomUUID();
        var first = service.request(professor, "Title", "Instructions");
        var second = service.request(professor, " Title ", "Instructions");

        assertThat(second).isSameAs(first);
        verify(jobs).save(org.mockito.ArgumentMatchers.any(TrainingActivityAiJob.class));
    }

    @Test
    void exactAcceptedSuggestionIsCachedAsSaveableWithoutAnotherModelJob() {
        var jobs = mock(TrainingActivityAiJobRepository.class);
        var engine = mock(InstructionReviewService.class);
        var source = "Diseña una evaluación de C.";
        var accepted = "Diseña una evaluación de C con cinco preguntas de dificultad media.";
        when(engine.hashInstructions(any(String.class))).thenAnswer(invocation ->
                invocation.<String>getArgument(0).contains(accepted) ? "accepted-hash" : "source-hash");
        when(jobs.fenceSuccess(any(), any(), any(), any(), anyInt(), any())).thenReturn(1);
        var service = new AdvisoryInstructionReviewService(jobs, engine);
        var professor = UUID.randomUUID();
        var job = new TrainingActivityAiJob();
        job.setId(UUID.randomUUID());
        job.setGeneration(2);
        job.setJobType(TrainingActivityAiJobType.INSTRUCTION_REVIEW);
        job.setReviewProfessorId(professor);
        job.setReviewTitle("Evaluación de C");
        job.setReviewInstructions(source);
        var result = new InstructionReviewResult(
                true,
                InstructionQualityStatus.NEEDS_IMPROVEMENT,
                false,
                false,
                "Conviene precisar cantidad y dificultad.",
                "NEEDS_IMPROVEMENT",
                List.of(new InstructionReviewIssue(
                        "specificity",
                        InstructionReviewIssueSeverity.WARNING,
                        "SPECIFICITY",
                        source,
                        0,
                        source.length(),
                        "Falta precisión.",
                        "Permite generar una evaluación consistente.",
                        accepted,
                        "Añade cantidad y dificultad.")),
                accepted,
                "accepted-hash",
                "source-hash",
                java.time.Instant.now(),
                "test-model",
                "test-rubric");

        assertThat(service.applySuccess(service.work(job), result)).isTrue();

        var derived = service.current(professor, "Evaluación de C", accepted);
        assertThat(derived).isNotNull();
        assertThat(derived.isSaveableGoodReview()).isTrue();
        assertThat(derived.issues()).isEmpty();
    }

    @Test
    void normalizedEntryExpiresThirtyMinutesAfterItsLastAccess() {
        var jobs = mock(TrainingActivityAiJobRepository.class);
        var engine = mock(InstructionReviewService.class);
        when(engine.hashInstructions(org.mockito.ArgumentMatchers.anyString())).thenReturn("review-hash");
        when(jobs.findFirstBySemanticKeyAndStatusInOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Optional.empty());
        var nanos = new AtomicLong();
        var service = new AdvisoryInstructionReviewService(jobs, engine, nanos::get);
        var professor = UUID.randomUUID();

        assertThat(service.current(professor, "Title", "Instructions")).isNull();
        var requested = service.request(professor, " Title ", "Instructions\r\nLine");
        nanos.addAndGet(TimeUnit.MINUTES.toNanos(29));
        assertThat(service.current(professor, "Title", "Instructions\nLine")).isSameAs(requested);
        nanos.addAndGet(TimeUnit.MINUTES.toNanos(31));
        assertThat(service.current(professor, "Title", "Instructions\nLine")).isNull();
    }
}
