package com.wornux.services.training_activity.instruction_review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.wornux.data.entities.training_activity.TrainingActivityAiJob;
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
