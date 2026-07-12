package com.wornux.services.training_activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAiJob;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobType;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityTurn;
import com.wornux.data.entities.training_activity.AnswerQuality;
import com.wornux.data.entities.training_activity.CoverageStatus;
import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.PedagogicalMove;
import com.wornux.data.entities.training_activity.TrainingActivityReport;
import com.wornux.data.repositories.training_activity.TrainingActivityAiJobRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityReportRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityTurnRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrainingTutorJobServiceTest {

    @Test
    void br22_reclaimedClaimFencesAnEarlierWorkerResult() {
        var fixture = fixture(2, 3, 8);
        fixture.job.setGeneration(9);

        var applied = fixture.service.applySuccess(fixture.job.getId(), 8, null);
        var failure = fixture.service.applyFailure(fixture.job.getId(), 8, "MODEL_TIMEOUT");

        assertThat(applied).isFalse();
        assertThat(failure.stale()).isTrue();
        assertThat(fixture.job.getStatus()).isEqualTo(TrainingActivityAiJobStatus.RUNNING);
        verify(fixture.assignmentRepository, never()).save(any());
    }

    @Test
    void af10_retryExhaustionPreservesAcceptedAnswerAndPublishesRecoverableState() {
        var fixture = fixture(3, 3, 4);
        fixture.turn.setAnswerText(" accepted answer ");

        var outcome = fixture.service.applyFailure(fixture.job.getId(), 4, "MODEL_TIMEOUT");

        assertThat(outcome.terminal()).isTrue();
        assertThat(fixture.job.getStatus()).isEqualTo(TrainingActivityAiJobStatus.FAILED);
        assertThat(fixture.assignment.getStatus()).isEqualTo(TrainingActivityAssignmentStatus.TEMPORARILY_UNAVAILABLE);
        assertThat(fixture.turn.getAnswerText()).isEqualTo(" accepted answer ");
    }

    @Test
    void br22_expiredClaimAtMaxAttemptsIsNotReturnedForAnotherClaim() {
        var fixture = fixture(3, 3, 4);
        fixture.job.setLeaseUntil(Instant.now().minusSeconds(1));
        when(fixture.jobRepository.findExpiredAtAttemptLimit(anyList(), eq(TrainingActivityAiJobStatus.RUNNING), any()))
                .thenReturn(List.of(fixture.job));
        when(fixture.jobRepository.findAvailableByTypes(anyList(), anyList(), eq(TrainingActivityAiJobStatus.RUNNING), any(), any()))
                .thenReturn(List.of());

        assertThat(fixture.service.availableTutorJobIds(Instant.now(), 4)).isEmpty();

        assertThat(fixture.job.getStatus()).isEqualTo(TrainingActivityAiJobStatus.FAILED);
        assertThat(fixture.assignment.getStatus()).isEqualTo(TrainingActivityAssignmentStatus.TEMPORARILY_UNAVAILABLE);
        verify(fixture.jobRepository, never()).claimTutor(any(), anyList(), any(), any(), any());
    }

    @Test
    void af10_manualRetryRefreshesTheJobInputVersion() {
        var fixture = fixture(3, 3, 4);
        ReflectionTestUtils.setField(
                fixture.assignment, "status", TrainingActivityAssignmentStatus.TEMPORARILY_UNAVAILABLE);
        ReflectionTestUtils.setField(fixture.assignment, "version", 7L);
        when(fixture.jobRepository.findFirstByAssignment_IdAndJobTypeInOrderByUpdatedAtDesc(
                eq(fixture.assignment.getId()), anyList())).thenReturn(Optional.of(fixture.job));

        assertThat(fixture.service.retryTemporaryFailure(fixture.assignment.getId())).isTrue();

        assertThat(fixture.job.getInputVersion()).isEqualTo(8L);
        assertThat(fixture.job.getStatus()).isEqualTo(TrainingActivityAiJobStatus.PENDING);
        assertThat(fixture.assignment.getStatus()).isEqualTo(TrainingActivityAssignmentStatus.WAITING_FOR_TUTOR);
    }

    @Test
    void af10_firstQuestionRetryResumesAnAlreadyPersistedQuestionWithoutAnotherModelCall() {
        var fixture = fixture(3, 3, 4);
        fixture.job.setJobType(TrainingActivityAiJobType.FIRST_QUESTION);
        fixture.job.setTurn(null);
        ReflectionTestUtils.setField(
                fixture.assignment, "status", TrainingActivityAssignmentStatus.TEMPORARILY_UNAVAILABLE);
        when(fixture.jobRepository.findFirstByAssignment_IdAndJobTypeInOrderByUpdatedAtDesc(
                eq(fixture.assignment.getId()), anyList())).thenReturn(Optional.of(fixture.job));
        when(fixture.turnRepository.findFirstByAssignment_IdAndAnswerTextIsNullOrderBySequenceNumberDesc(
                fixture.assignment.getId())).thenReturn(Optional.of(fixture.turn));

        assertThat(fixture.service.retryTemporaryFailure(fixture.assignment.getId())).isTrue();

        assertThat(fixture.job.getStatus()).isEqualTo(TrainingActivityAiJobStatus.SUCCEEDED);
        assertThat(fixture.job.getLastErrorCode()).isNull();
        assertThat(fixture.assignment.getStatus()).isEqualTo(TrainingActivityAssignmentStatus.WAITING_FOR_ANSWER);
    }

    @Test
    void br22_claimReturnsTheGenerationThatOwnsResultApplication() {
        var fixture = fixture(0, 3, 11);
        when(fixture.jobRepository.claimTutor(eq(fixture.job.getId()), anyList(), eq(TrainingActivityAiJobStatus.RUNNING), any(), any()))
                .thenReturn(1);
        when(fixture.assignmentRepository.findWithTrainingActivityById(fixture.assignment.getId())).thenReturn(Optional.of(fixture.assignment));
        when(fixture.turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(fixture.assignment.getId())).thenReturn(List.of(fixture.turn));

        var work = fixture.service.claim(fixture.job.getId(), Instant.now(), Instant.now().plusSeconds(30));

        assertThat(work.ownershipGeneration()).isEqualTo(11);
    }

    @Test
    void br22_safeBrowserHeartbeatVersionChangeDoesNotDiscardFirstQuestion() {
        var fixture = fixture(1, 3, 4);
        fixture.job.setJobType(TrainingActivityAiJobType.FIRST_QUESTION);
        fixture.job.setTurn(null);
        fixture.job.setInputVersion(1);
        ReflectionTestUtils.setField(fixture.assignment, "status", TrainingActivityAssignmentStatus.STARTING);
        ReflectionTestUtils.setField(fixture.assignment, "version", 5L);
        var decision = new AdaptiveTutorDecision(TutorDecisionType.QUESTION, AnswerQuality.GOOD,
                EvidenceStatus.PARTIAL_EVIDENCE, CoverageStatus.PARTIAL, PedagogicalMove.ASK_FOR_CLARITY,
                false, List.of(), List.of(), false, "¿Cuál es el caso base?", "QUESTION");

        assertThat(fixture.service.applySuccess(fixture.job.getId(), 4, decision)).isTrue();
        assertThat(fixture.assignment.getStatus()).isEqualTo(TrainingActivityAssignmentStatus.WAITING_FOR_ANSWER);
        verify(fixture.turnRepository).save(any(TrainingActivityTurn.class));
    }

    @Test
    void br25_terminalDecisionUsesAtomicReportAndSemanticJobInsertion() {
        var fixture = fixture(1, 3, 4);
        fixture.turn.setAnswerText("accepted answer");
        fixture.job.setLastErrorCode("MODEL_TIMEOUT");
        var report = new TrainingActivityReport();
        report.setId(UUID.randomUUID());
        when(fixture.reportRepository.findByAssignment_Id(fixture.assignment.getId())).thenReturn(Optional.of(report));
        var decision = new AdaptiveTutorDecision(TutorDecisionType.COMPLETE_SUCCESS, AnswerQuality.GOOD,
                EvidenceStatus.STRONG_EVIDENCE, CoverageStatus.SUFFICIENT, PedagogicalMove.COMPLETE_SUCCESSFULLY,
                false, List.of("pointer traversal"), List.of(), false, "", "COMPLETE_SUCCESS");

        assertThat(fixture.service.applySuccess(fixture.job.getId(), 4, decision)).isTrue();
        assertThat(fixture.job.getLastErrorCode()).isNull();

        verify(fixture.reportRepository).insertPendingIfAbsent(any(), eq(fixture.assignment.getId()), eq("STRONG_EVIDENCE"), any(), any(), any());
        verify(fixture.jobRepository).insertTutorJobIfAbsent(any(), eq("FINAL_REPORT"), anyInt(), any(),
                eq(fixture.assignment.getId()), any(), eq(report.getId()), anyLong(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void multilineAcceptedAnswerCanReceiveTheTutorDecision() {
        var fixture = fixture(1, 3, 4);
        fixture.turn.setAnswerText("""
                A runtime assertion checks the index before access.

                ```c
                assert(index < length);
                ```
                """);
        var decision = new AdaptiveTutorDecision(TutorDecisionType.QUESTION, AnswerQuality.GOOD,
                EvidenceStatus.PARTIAL_EVIDENCE, CoverageStatus.PARTIAL, PedagogicalMove.ASK_FOR_JUSTIFICATION,
                false, List.of("runtime assertion"), List.of(), false,
                "¿Por qué conviene validar antes del acceso?", "QUESTION");

        assertThat(fixture.service.applySuccess(fixture.job.getId(), 4, decision)).isTrue();
        assertThat(fixture.assignment.getStatus()).isEqualTo(TrainingActivityAssignmentStatus.WAITING_FOR_ANSWER);
        assertThat(fixture.job.getLastErrorCode()).isNull();
    }

    private static Fixture fixture(int attempts, int maxAttempts, int generation) {
        var jobRepository = mock(TrainingActivityAiJobRepository.class);
        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        var turnRepository = mock(TrainingActivityTurnRepository.class);
        var assignment = assignment();
        var turn = new TrainingActivityTurn();
        turn.setId(UUID.randomUUID());
        turn.setAssignment(assignment);
        turn.setSequenceNumber(1);
        turn.setQuestionText("¿Qué entiendes?");
        turn.setQuestionCreatedAt(Instant.now());
        var job = new TrainingActivityAiJob();
        job.setId(UUID.randomUUID());
        job.setJobType(TrainingActivityAiJobType.NEXT_DECISION);
        job.setAssignment(assignment);
        job.setTurn(turn);
        job.setStatus(TrainingActivityAiJobStatus.RUNNING);
        job.setAttemptCount(attempts);
        job.setMaxAttempts(maxAttempts);
        job.setGeneration(generation);
        job.setInputVersion(0);
        job.setLeaseUntil(Instant.now().plusSeconds(30));
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(assignmentRepository.findLockedWithTrainingActivityById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(turnRepository.findById(turn.getId())).thenReturn(Optional.of(turn));
        var reportRepository = mock(TrainingActivityReportRepository.class);
        var service = new TrainingTutorJobService(jobRepository, assignmentRepository, turnRepository,
                reportRepository, mock(TrainingAssignmentTutorService.class), new SafeBrowserAssignmentStateBus());
        return new Fixture(service, jobRepository, assignmentRepository, turnRepository, reportRepository, assignment, turn, job);
    }

    private static TrainingActivityAssignment assignment() {
        var activity = new TrainingActivity();
        ReflectionTestUtils.setField(activity, "id", UUID.randomUUID());
        var member = new GroupClassMember();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        var assignment = new TrainingActivityAssignment();
        ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(assignment, "trainingActivity", activity);
        ReflectionTestUtils.setField(assignment, "groupClassMember", member);
        ReflectionTestUtils.setField(assignment, "status", TrainingActivityAssignmentStatus.WAITING_FOR_TUTOR);
        ReflectionTestUtils.setField(assignment, "updatedAt", Instant.now());
        return assignment;
    }

    private record Fixture(TrainingTutorJobService service, TrainingActivityAiJobRepository jobRepository,
                           TrainingActivityAssignmentRepository assignmentRepository, TrainingActivityTurnRepository turnRepository,
                           TrainingActivityReportRepository reportRepository,
                           TrainingActivityAssignment assignment, TrainingActivityTurn turn, TrainingActivityAiJob job) { }
}
