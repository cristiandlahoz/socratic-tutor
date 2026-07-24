package com.wornux.specdriven.usecases.uc003_student_training_evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.AnswerQuality;
import com.wornux.data.entities.training_activity.CoverageStatus;
import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.PedagogicalMove;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAiJob;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobType;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus;
import com.wornux.data.entities.training_activity.TrainingActivityReport;
import com.wornux.data.entities.training_activity.TrainingActivityReportStatus;
import com.wornux.data.entities.training_activity.TrainingActivityTurn;
import com.wornux.data.repositories.training_activity.TrainingActivityAiJobRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityReportRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityTurnRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.training_activity.AdaptiveTutorDecision;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.TrainingActivityReportProjectionService;
import com.wornux.services.training_activity.TrainingAssignmentEvaluationService;
import com.wornux.services.training_activity.TrainingAssignmentTutorService;
import com.wornux.services.training_activity.TrainingTutorJobService;
import com.wornux.services.training_activity.TutorDecisionType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class UC003StudentTrainingEvaluation {

    @Test
    void mainFlow_persistsStudentEvidenceBeforeTerminalSubmissionAndProfessorProjection() throws Exception {
        var fixture = fixture();

        fixture.evaluationService.start(fixture.assignment.getId());
        assertThat(fixture.assignment.getStatus()).isEqualTo(TrainingActivityAssignmentStatus.STARTING);
        verify(fixture.jobRepository).insertTutorJobIfAbsent(any(), eq("FIRST_QUESTION"), anyInt(), any(), any(), any(), any(),
                anyLong(), anyString(), anyInt(), any(), any(), any());

        fixture.assignment.setStatus(TrainingActivityAssignmentStatus.WAITING_FOR_ANSWER);
        fixture.evaluationService.submitAnswer(fixture.assignment.getId(), "Visito cada nodo hasta llegar a null.", fixture.submissionId);

        assertThat(fixture.turn.getAnswerText()).isEqualTo("Visito cada nodo hasta llegar a null.");
        assertThat(fixture.assignment.getStatus()).isEqualTo(TrainingActivityAssignmentStatus.WAITING_FOR_TUTOR);
        verify(fixture.jobRepository).insertTutorJobIfAbsent(any(), eq("NEXT_DECISION"), anyInt(), any(), any(), any(), any(),
                anyLong(), anyString(), anyInt(), any(), any(), any());

        var report = new TrainingActivityReport();
        report.setId(UUID.randomUUID());
        report.setStatus(TrainingActivityReportStatus.PENDING);
        var tutorService = mock(TrainingAssignmentTutorService.class);
        when(tutorService.currentModelName()).thenReturn("test-model");
        when(tutorService.promptVersion()).thenReturn("test-prompt");
        var reportRepository = mock(TrainingActivityReportRepository.class);
        when(reportRepository.findByAssignment_Id(fixture.assignment.getId())).thenReturn(Optional.of(report));
        var decisionJob = terminalDecisionJob(fixture.assignment, fixture.turn);
        when(fixture.jobRepository.findById(decisionJob.getId())).thenReturn(Optional.of(decisionJob));
        when(fixture.turnRepository.findById(fixture.turn.getId())).thenReturn(Optional.of(fixture.turn));
        var tutorJobs = new TrainingTutorJobService(fixture.jobRepository, fixture.assignmentRepository, fixture.turnRepository,
                reportRepository, tutorService, new SafeBrowserAssignmentStateBus());

        assertThat(tutorJobs.applySuccess(decisionJob.getId(), decisionJob.getGeneration(), terminalDecision())).isTrue();
        assertThat(fixture.assignment.getStatus()).isEqualTo(TrainingActivityAssignmentStatus.SUBMITTED);
        verify(reportRepository).insertPendingIfAbsent(any(), eq(fixture.assignment.getId()), eq("STRONG_EVIDENCE"), any(), any(), any());
        verify(fixture.jobRepository).insertTutorJobIfAbsent(any(), eq("FINAL_REPORT"), anyInt(), any(), eq(fixture.assignment.getId()),
                any(), eq(report.getId()), anyLong(), anyString(), anyInt(), any(), any(), any());

        var professorContext = new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                fixture.groupClassId, GroupClassMemberKind.PROFESSOR);
        when(fixture.contextResolver.requireCurrent()).thenReturn(professorContext);
        when(fixture.assignmentRepository.findWithTrainingActivityById(fixture.assignment.getId()))
                .thenReturn(Optional.of(fixture.assignment));
        var projection = new TrainingActivityReportProjectionService(fixture.assignmentRepository, reportRepository,
                fixture.turnRepository, fixture.contextResolver, tutorJobs).getForCurrentReviewer(fixture.assignment.getId());

        assertThat(projection.status()).isEqualTo(TrainingActivityReportStatus.PENDING);
        assertThat(projection.turns()).extracting(TrainingActivityReportProjectionService.TurnProjection::questionText,
                TrainingActivityReportProjectionService.TurnProjection::answerText)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("¿Cómo recorres una lista enlazada?",
                        "Visito cada nodo hasta llegar a null."));
    }

    @Test
    void af1_af2_ownershipSafeBrowserAndTimeWindowRejectCommandsBeforeAnyJob() {
        var fixture = fixture();
        fixture.activity.setOpensAt(Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> fixture.evaluationService.start(fixture.assignment.getId())).isInstanceOf(IllegalStateException.class);
        verify(fixture.jobRepository, never()).insertTutorJobIfAbsent(any(), anyString(), anyInt(), any(), any(), any(), any(),
                anyLong(), anyString(), anyInt(), any(), any(), any());

        fixture.activity.setOpensAt(null);
        fixture.activity.setSafeBrowserEnabled(true);
        assertThatThrownBy(() -> fixture.evaluationService.start(fixture.assignment.getId())).isInstanceOf(IllegalStateException.class);

        var otherStudent = new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                fixture.groupClassId, GroupClassMemberKind.STUDENT);
        when(fixture.contextResolver.requireCurrent()).thenReturn(otherStudent);
        assertThatThrownBy(() -> fixture.evaluationService.getForCurrentStudent(fixture.assignment.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void af7_recoveryUsesPersistedTurnsAndTheSameSubmissionIdDoesNotAdvanceTwice() {
        var fixture = fixture();
        fixture.assignment.setStatus(TrainingActivityAssignmentStatus.WAITING_FOR_ANSWER);
        fixture.turn.setAnswerText("Respuesta conservada");
        fixture.turn.setAnswerSubmissionId(fixture.submissionId);
        fixture.turn.setAnswerSubmittedAt(Instant.now());
        when(fixture.turnRepository.findByAssignment_IdAndAnswerSubmissionId(fixture.assignment.getId(), fixture.submissionId))
                .thenReturn(Optional.of(fixture.turn));

        var reloaded = fixture.evaluationService.getForCurrentStudent(fixture.assignment.getId());
        fixture.evaluationService.submitAnswer(fixture.assignment.getId(), "Respuesta conservada", fixture.submissionId);

        assertThat(reloaded.currentQuestion()).isNull();
        verify(fixture.turnRepository, never()).save(any());
        verify(fixture.jobRepository, never()).insertTutorJobIfAbsent(any(), anyString(), anyInt(), any(), any(), any(), any(),
                anyLong(), anyString(), anyInt(), any(), any(), any());
    }

    @Test
    void br_turnSubmissionAndLiveSafeBrowserConstraintsRemainInTheBaseline() throws Exception {
        var sql = new ClassPathResource("db/migration/prod/V1__baseline.sql").getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("uk_training_activity_turn_assignment_submission unique (training_activity_assignment_id, answer_submission_id)")
                .contains("chk_training_activity_turn_answer_pair")
                .contains("uk_safe_browser_session_live_assignment")
                .contains("uk_safe_browser_event_session_client_event");
    }

    private static AdaptiveTutorDecision terminalDecision() {
        return new AdaptiveTutorDecision(TutorDecisionType.COMPLETE_SUCCESS, AnswerQuality.GOOD, EvidenceStatus.STRONG_EVIDENCE,
                CoverageStatus.SUFFICIENT, PedagogicalMove.COMPLETE_SUCCESSFULLY, false, List.of("recorrido"), List.of(), false,
                "", "COMPLETE_SUCCESS");
    }

    private static TrainingActivityAiJob terminalDecisionJob(TrainingActivityAssignment assignment, TrainingActivityTurn turn) {
        var job = new TrainingActivityAiJob();
        job.setId(UUID.randomUUID());
        job.setJobType(TrainingActivityAiJobType.NEXT_DECISION);
        job.setAssignment(assignment);
        job.setTurn(turn);
        job.setStatus(TrainingActivityAiJobStatus.RUNNING);
        job.setGeneration(1);
        job.setLeaseUntil(Instant.now().plusSeconds(30));
        job.setInputVersion(assignment.getVersion());
        job.setMaxAttempts(3);
        return job;
    }

    private static Fixture fixture() {
        var groupClassId = UUID.randomUUID();
        var group = new GroupClass();
        group.setId(groupClassId);
        var activity = new TrainingActivity();
        activity.setId(UUID.randomUUID());
        activity.setGroupClass(group);
        activity.setStatus(TrainingActivityLifecycleStatus.PUBLISHED);
        activity.setTitle("Listas enlazadas");
        activity.setInstructions("Explica tu recorrido.");
        var memberId = UUID.randomUUID();
        var member = new GroupClassMember();
        member.setId(memberId);
        var assignment = new TrainingActivityAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTrainingActivity(activity);
        assignment.setGroupClassMember(member);
        assignment.setStatus(TrainingActivityAssignmentStatus.ASSIGNED);
        assignment.setAssignedAt(Instant.now());
        assignment.setUpdatedAt(Instant.now());
        var turn = new TrainingActivityTurn();
        turn.setId(UUID.randomUUID());
        turn.setAssignment(assignment);
        turn.setSequenceNumber(1);
        turn.setQuestionText("¿Cómo recorres una lista enlazada?");
        turn.setQuestionCreatedAt(Instant.now());
        turn.setCreatedAt(Instant.now());
        turn.setUpdatedAt(Instant.now());

        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        when(assignmentRepository.findLockedWithTrainingActivityById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(assignmentRepository.findWithTrainingActivityById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var turnRepository = mock(TrainingActivityTurnRepository.class);
        when(turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(assignment.getId())).thenReturn(List.of(turn));
        when(turnRepository.findFirstByAssignment_IdAndAnswerTextIsNullOrderBySequenceNumberDesc(assignment.getId()))
                .thenReturn(Optional.of(turn));
        var jobRepository = mock(TrainingActivityAiJobRepository.class);
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), memberId,
                groupClassId, GroupClassMemberKind.STUDENT));
        return new Fixture(new TrainingAssignmentEvaluationService(assignmentRepository, turnRepository, jobRepository, contextResolver,
                mock(TrainingTutorJobService.class)),
                assignmentRepository, turnRepository, jobRepository, contextResolver, activity, assignment, turn, groupClassId,
                UUID.randomUUID());
    }

    private record Fixture(
            TrainingAssignmentEvaluationService evaluationService,
            TrainingActivityAssignmentRepository assignmentRepository,
            TrainingActivityTurnRepository turnRepository,
            TrainingActivityAiJobRepository jobRepository,
            ActiveAcademicContextResolver contextResolver,
            TrainingActivity activity,
            TrainingActivityAssignment assignment,
            TrainingActivityTurn turn,
            UUID groupClassId,
            UUID submissionId) {}
}
