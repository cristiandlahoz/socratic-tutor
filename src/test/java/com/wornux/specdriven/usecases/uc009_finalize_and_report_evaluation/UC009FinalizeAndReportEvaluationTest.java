package com.wornux.specdriven.usecases.uc009_finalize_and_report_evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClass;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAiJob;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobType;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.TrainingActivityReport;
import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.TrainingActivityReportStatus;
import com.wornux.data.entities.training_activity.TrainingActivityTurn;
import com.wornux.data.repositories.training_activity.TrainingActivityAiJobRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityReportRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityTurnRepository;
import com.wornux.services.context.ActiveAcademicContext;
import com.wornux.services.context.ActiveAcademicContextResolver;
import com.wornux.services.training_activity.FinalReportCandidate;
import com.wornux.services.training_activity.FinalReportCandidateValidationException;
import com.wornux.services.training_activity.FinalReportCandidateValidationException.Reason;
import com.wornux.services.training_activity.SafeBrowserAssignmentStateBus;
import com.wornux.services.training_activity.TrainingActivityReportProjectionService;
import com.wornux.services.training_activity.TrainingAssignmentTutorService;
import com.wornux.services.training_activity.TrainingTutorJobService;
import org.junit.jupiter.api.Test;

class UC009FinalizeAndReportEvaluationTest {

    @Test
    void af8_studentIsDeniedBeforeAnyReportOrTranscriptRead() {
        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(context(UUID.randomUUID(), GroupClassMemberKind.STUDENT));
        var service = projectionService(assignmentRepository, contextResolver);

        assertThatThrownBy(() -> service.getForCurrentReviewer(UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
        verify(assignmentRepository, never()).findWithTrainingActivityById(any());
    }

    @Test
    void af8_professorFromAnotherGroupIsDeniedWithoutReportContent() {
        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        var assignment = submittedAssignment(UUID.randomUUID());
        when(assignmentRepository.findWithTrainingActivityById(assignment.getId())).thenReturn(Optional.of(assignment));
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(context(UUID.randomUUID(), GroupClassMemberKind.PROFESSOR));
        var service = projectionService(assignmentRepository, contextResolver);

        assertThatThrownBy(() -> service.getForCurrentReviewer(assignment.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void af6_reclaimedWorkerCannotPublishAfterItsGenerationFenceIsLost() {
        var fixture = reportFixture();
        when(fixture.jobRepository.fenceSuccess(eq(fixture.job.getId()), eq(TrainingActivityAiJobType.FINAL_REPORT),
                eq(TrainingActivityAiJobStatus.RUNNING), eq(TrainingActivityAiJobStatus.SUCCEEDED), eq(4), any())).thenReturn(0);

        var applied = fixture.service.applyFinalReportSuccess(fixture.job.getId(), 4, weakCandidate("respuesta limitada"));

        assertThat(applied).isFalse();
        verify(fixture.reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void af3_invalidFabricatedObservationIsRejectedBeforeItCanBecomeReady() {
        var fixture = reportFixture();
        var candidate = new FinalReportCandidate(
                EvidenceStatus.WEAK_EVIDENCE,
                "La evidencia es limitada y no permite una conclusión sólida.",
                List.of(),
                List.of(new FinalReportCandidate.ReportFinding("dominio inventado sin ancla", List.of(
                        new FinalReportCandidate.EvidenceReference(99, null, "respuesta limitada")))),
                List.of(),
                List.of("Solicitar una explicación concreta."));

        assertValidationFailure(fixture, candidate, Reason.INVALID_TURN_REFERENCE);
        assertThat(fixture.job.getReport().getStatus()).isEqualTo(TrainingActivityReportStatus.GENERATING);
        verify(fixture.reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void br11_genericSharedTokenWithoutStructuredProvenanceIsRejected() {
        var fixture = reportFixture();
        var candidate = weakCandidate("respuesta", List.of());

        assertValidationFailure(fixture, candidate, Reason.MISSING_EVIDENCE_REFERENCE);
    }

    @Test
    void br11_nonexistentTurnSequenceIsRejected() {
        var fixture = reportFixture();
        var candidate = weakCandidate("respuesta limitada", List.of(reference(2, null, "respuesta limitada")));

        assertValidationFailure(fixture, candidate, Reason.INVALID_TURN_REFERENCE);
    }

    @Test
    void br11_crossAssignmentTurnSequenceIsRejected() {
        var fixture = reportFixture();
        var foreignTurn = new TrainingActivityTurn();
        foreignTurn.setAssignment(submittedAssignment(UUID.randomUUID()));
        foreignTurn.setSequenceNumber(2);
        foreignTurn.setQuestionText("Pregunta de otra evaluación");
        foreignTurn.setAnswerText("respuesta limitada");
        var candidate = weakCandidate("respuesta limitada", List.of(reference(2, null, "respuesta limitada")));

        assertThat(foreignTurn.getAssignment().getId()).isNotEqualTo(fixture.job.getAssignment().getId());

        assertValidationFailure(fixture, candidate, Reason.INVALID_TURN_REFERENCE);
    }

    @Test
    void br11_inventedExcerptIsRejected() {
        var fixture = reportFixture();
        var candidate = weakCandidate("respuesta limitada", List.of(reference(1, "pregunta inventada", "respuesta limitada")));

        assertValidationFailure(fixture, candidate, Reason.QUESTION_EXCERPT_MISMATCH);
    }

    @Test
    void br11_exactWhitespaceNormalizedExcerptIsAcceptedAndPersistsOnlyTheCanonicalSequence() {
        var fixture = reportFixture();
        fixture.turn.setQuestionText("¿Qué\u00a0observaste?\n");
        fixture.turn.setAnswerText("respuesta\n\tlimitada");
        var candidate = weakCandidate("respuesta limitada", List.of(reference(1, "¿Qué observaste?", "respuesta limitada")));
        when(fixture.jobRepository.fenceSuccess(eq(fixture.job.getId()), eq(TrainingActivityAiJobType.FINAL_REPORT),
                eq(TrainingActivityAiJobStatus.RUNNING), eq(TrainingActivityAiJobStatus.SUCCEEDED), eq(4), any())).thenReturn(1);

        assertThat(fixture.service.applyFinalReportSuccess(fixture.job.getId(), 4, candidate)).isTrue();
        assertThat(fixture.job.getReport().getWeaknesses()).singleElement().satisfies(finding ->
                assertThat(finding.evidenceReferences()).extracting(reference -> reference.turnSequence()).containsExactly(1));
    }

    @Test
    void af7_noSeIsValidInsufficientEvidenceCitation() {
        var fixture = reportFixture();
        fixture.turn.setAnswerText("no sé");
        var candidate = weakCandidate("La respuesta no permite concluir dominio.", List.of(reference(1, null, "no sé")));
        when(fixture.jobRepository.fenceSuccess(eq(fixture.job.getId()), eq(TrainingActivityAiJobType.FINAL_REPORT),
                eq(TrainingActivityAiJobStatus.RUNNING), eq(TrainingActivityAiJobStatus.SUCCEEDED), eq(4), any())).thenReturn(1);

        assertThat(fixture.service.applyFinalReportSuccess(fixture.job.getId(), 4, candidate)).isTrue();
    }

    @Test
    void br11_studentCorrectionWithCanonicalCitationIsAccepted() {
        var fixture = reportFixture();
        fixture.turn.setAnswerText("No, el for sin llaves compila cuando controla una sola instrucción.");
        var candidate = weakCandidate("El estudiante corrige la premisa sobre el bucle.",
                List.of(reference(1, null, "for sin llaves compila")));
        when(fixture.jobRepository.fenceSuccess(eq(fixture.job.getId()), eq(TrainingActivityAiJobType.FINAL_REPORT),
                eq(TrainingActivityAiJobStatus.RUNNING), eq(TrainingActivityAiJobStatus.SUCCEEDED), eq(4), any())).thenReturn(1);

        assertThat(fixture.service.applyFinalReportSuccess(fixture.job.getId(), 4, candidate)).isTrue();
    }

    @Test
    void af7_weakEvidenceRequiresAnExplicitLimitationAndNoStrengths() {
        var fixture = reportFixture();
        var candidate = new FinalReportCandidate(
                EvidenceStatus.WEAK_EVIDENCE,
                "Hay evidencia limitada para una conclusión.",
                List.of(new FinalReportCandidate.ReportFinding("respuesta limitada", List.of(reference(1, null, "respuesta limitada")))),
                List.of(),
                List.of(new FinalReportCandidate.ReportFinding("respuesta limitada", List.of(reference(1, null, "respuesta limitada")))),
                List.of("Solicitar una explicación concreta."));

        assertValidationFailure(fixture, candidate, Reason.WEAK_EVIDENCE_CONTRACT_MISMATCH);
    }

    @Test
    void br11_answerExcerptMismatchIsRejectedWithItsSafeReason() {
        var fixture = reportFixture();
        var candidate = weakCandidate("respuesta limitada", List.of(reference(1, null, "respuesta inventada")));

        assertValidationFailure(fixture, candidate, Reason.ANSWER_EXCERPT_MISMATCH);
    }

    @Test
    void br11_nullCandidateIsRejectedWithItsSafeReason() {
        var fixture = reportFixture();

        assertValidationFailure(fixture, null, Reason.NULL_CANDIDATE);
    }

    @Test
    void br11_missingEvidenceStatusIsRejectedWithItsSafeReason() {
        var fixture = reportFixture();
        var missingStatus = new FinalReportCandidate(
                null,
                "La evidencia es limitada y no permite una conclusión sólida.",
                List.of(), List.of(), List.of(), List.of("Solicitar una explicación concreta."));

        assertValidationFailure(fixture, missingStatus, Reason.MISSING_EVIDENCE_STATUS);
    }

    @Test
    void af7_noEvidenceCandidateBecomesReadyWithTheAuthoritativeStatus() {
        var fixture = reportFixture();
        fixture.job.getAssignment().setEvidenceStatus(EvidenceStatus.NO_EVIDENCE);
        when(fixture.jobRepository.fenceSuccess(eq(fixture.job.getId()), eq(TrainingActivityAiJobType.FINAL_REPORT),
                eq(TrainingActivityAiJobStatus.RUNNING), eq(TrainingActivityAiJobStatus.SUCCEEDED), eq(4), any())).thenReturn(1);

        var candidate = new FinalReportCandidate(
                EvidenceStatus.NO_EVIDENCE,
                "No hay evidencia observable suficiente para alcanzar una conclusión defendible.",
                List.of(),
                List.of(),
                List.of(),
                List.of("Repetir con una pregunta más acotada y solicitar razonamiento o un ejemplo concreto."));

        assertThat(fixture.service.applyFinalReportSuccess(fixture.job.getId(), 4, candidate)).isTrue();

        assertThat(fixture.job.getReport().getStatus()).isEqualTo(TrainingActivityReportStatus.READY);
        assertThat(fixture.job.getReport().getEvidenceStatus()).isEqualTo(EvidenceStatus.NO_EVIDENCE);
        assertThat(fixture.job.getReport().getStrengths()).isEmpty();
        assertThat(fixture.job.getReport().getWeaknesses()).isEmpty();
        assertThat(fixture.job.getReport().getObservations()).isEmpty();
        assertThat(fixture.job.getReport().getRecommendations()).containsExactly(
                "Repetir con una pregunta más acotada y solicitar razonamiento o un ejemplo concreto.");
    }

    @Test
    void af7_noEvidenceAcceptsExplicitAbsenceOrInsufficiencyStatements() {
        for (var summary : List.of(
                "No hay evidencia suficiente para una conclusión defendible.",
                "Sin evidencia, no se puede concluir.",
                "La evidencia es insuficiente para concluir.",
                "La evidencia no permite concluir.",
                "Insufficient evidence to reach a conclusion.",
                "No evidence supports a conclusion.")) {
            var fixture = noEvidenceReportFixture();
            fixture.job.getAssignment().setEvidenceStatus(EvidenceStatus.NO_EVIDENCE);
            when(fixture.jobRepository.fenceSuccess(eq(fixture.job.getId()), eq(TrainingActivityAiJobType.FINAL_REPORT),
                    eq(TrainingActivityAiJobStatus.RUNNING), eq(TrainingActivityAiJobStatus.SUCCEEDED), eq(4), any())).thenReturn(1);

            assertThat(fixture.service.applyFinalReportSuccess(fixture.job.getId(), 4, noEvidenceCandidate(summary))).isTrue();
        }
    }

    @Test
    void af7_noEvidenceRejectsAnyDiagnosticFinding() {
        for (var candidate : List.of(
                new FinalReportCandidate(EvidenceStatus.NO_EVIDENCE, "No hay evidencia suficiente para una conclusión defendible.",
                        List.of(new FinalReportCandidate.ReportFinding("fortaleza", List.of())), List.of(), List.of(),
                        List.of("Solicitar una respuesta explicada.")),
                new FinalReportCandidate(EvidenceStatus.NO_EVIDENCE, "No hay evidencia suficiente para una conclusión defendible.",
                        List.of(), List.of(new FinalReportCandidate.ReportFinding("debilidad", List.of())), List.of(),
                        List.of("Solicitar una respuesta explicada.")),
                new FinalReportCandidate(EvidenceStatus.NO_EVIDENCE, "No hay evidencia suficiente para una conclusión defendible.",
                        List.of(), List.of(), List.of(new FinalReportCandidate.ReportFinding("observación", List.of())),
                        List.of("Solicitar una respuesta explicada.")))) {
            var fixture = noEvidenceReportFixture();
            fixture.job.getAssignment().setEvidenceStatus(EvidenceStatus.NO_EVIDENCE);

            assertValidationFailure(fixture, candidate, Reason.NO_EVIDENCE_CONTRACT_MISMATCH);
        }
    }

    @Test
    void af7_noEvidenceRequiresAStatementOfInsufficientEvidence() {
        var fixture = noEvidenceReportFixture();
        fixture.job.getAssignment().setEvidenceStatus(EvidenceStatus.NO_EVIDENCE);

        assertValidationFailure(fixture, noEvidenceCandidate("El reporte no tiene datos concluyentes."),
                Reason.NO_EVIDENCE_CONTRACT_MISMATCH);
    }

    @Test
    void af7_noEvidenceRejectsMerelyLimitedEvidence() {
        var fixture = noEvidenceReportFixture();
        fixture.job.getAssignment().setEvidenceStatus(EvidenceStatus.NO_EVIDENCE);

        assertValidationFailure(fixture, noEvidenceCandidate("La evidencia es limitada."),
                Reason.NO_EVIDENCE_CONTRACT_MISMATCH);
    }

    @Test
    void af7_noEvidenceRequiresConcreteRecommendations() {
        var fixture = noEvidenceReportFixture();
        fixture.job.getAssignment().setEvidenceStatus(EvidenceStatus.NO_EVIDENCE);
        var candidate = new FinalReportCandidate(EvidenceStatus.NO_EVIDENCE,
                "No hay evidencia suficiente para una conclusión defendible.", List.of(), List.of(), List.of(), List.of());

        assertValidationFailure(fixture, candidate, Reason.MISSING_RECOMMENDATIONS);
    }

    @Test
    void br11_evidenceStatusMismatchIsRejectedWithItsSafeReason() {
        var fixture = reportFixture();
        var candidate = new FinalReportCandidate(
                EvidenceStatus.PARTIAL_EVIDENCE,
                "La evidencia es limitada y no permite una conclusión sólida.",
                List.of(), List.of(), List.of(), List.of("Solicitar una explicación concreta."));

        assertValidationFailure(fixture, candidate, Reason.EVIDENCE_STATUS_MISMATCH);
    }

    @Test
    void br11_invalidSummaryIsRejectedWithItsSafeReason() {
        var fixture = reportFixture();
        var candidate = new FinalReportCandidate(
                EvidenceStatus.WEAK_EVIDENCE, " ", List.of(), List.of(), List.of(),
                List.of("Solicitar una explicación concreta."));

        assertValidationFailure(fixture, candidate, Reason.INVALID_SUMMARY);
    }

    @Test
    void br11_missingFindingCollectionsAreRejectedWithTheirSafeReason() {
        var fixture = reportFixture();
        var candidate = new FinalReportCandidate(
                EvidenceStatus.WEAK_EVIDENCE,
                "La evidencia es limitada y no permite una conclusión sólida.",
                null, List.of(), List.of(), List.of("Solicitar una explicación concreta."));

        assertValidationFailure(fixture, candidate, Reason.MISSING_FINDING_COLLECTIONS);
    }

    @Test
    void br11_missingRecommendationsAreRejectedWithTheirSafeReason() {
        var fixture = reportFixture();
        var candidate = new FinalReportCandidate(
                EvidenceStatus.WEAK_EVIDENCE,
                "La evidencia es limitada y no permite una conclusión sólida.",
                List.of(), List.of(), List.of(), List.of());

        assertValidationFailure(fixture, candidate, Reason.MISSING_RECOMMENDATIONS);
    }

    @Test
    void br11_tooManyRecommendationsAreRejectedWithTheirSafeReason() {
        var fixture = reportFixture();
        var candidate = new FinalReportCandidate(
                EvidenceStatus.WEAK_EVIDENCE,
                "La evidencia es limitada y no permite una conclusión sólida.",
                List.of(), List.of(), List.of(),
                java.util.Collections.nCopies(9, "Solicitar una explicación concreta."));

        assertValidationFailure(fixture, candidate, Reason.TOO_MANY_RECOMMENDATIONS);
    }

    @Test
    void br11_invalidRecommendationIsRejectedWithItsSafeReason() {
        var fixture = reportFixture();
        var candidate = new FinalReportCandidate(
                EvidenceStatus.WEAK_EVIDENCE,
                "La evidencia es limitada y no permite una conclusión sólida.",
                List.of(), List.of(), List.of(), List.of(" "));

        assertValidationFailure(fixture, candidate, Reason.INVALID_RECOMMENDATION);
    }

    @Test
    void br11_invalidFindingIsRejectedWithItsSafeReason() {
        var fixture = reportFixture();

        assertValidationFailure(fixture, weakCandidate(" "), Reason.FINDING_LIMIT_EXCEEDED_OR_INVALID_TEXT);
    }

    @Test
    void af2_transientFailureReturnsReportToPendingWithoutChangingSubmissionOrTurns() {
        var fixture = reportFixture();
        fixture.job.setAttemptCount(1);
        fixture.job.setMaxAttempts(3);
        when(fixture.jobRepository.fenceFailure(eq(fixture.job.getId()),
                eq(TrainingActivityAiJobType.FINAL_REPORT), eq(TrainingActivityAiJobStatus.RUNNING),
                eq(TrainingActivityAiJobStatus.RETRYABLE), eq(4), any(), eq("MODEL_TIMEOUT"), eq(3L), any()))
                .thenReturn(1);

        var outcome = fixture.service.applyFinalReportFailure(fixture.job.getId(), 4, "MODEL_TIMEOUT");

        assertThat(outcome.retryScheduled()).isTrue();
        assertThat(fixture.job.getAssignment().getStatus()).isEqualTo(TrainingActivityAssignmentStatus.SUBMITTED);
        assertThat(fixture.job.getReport().getStatus()).isEqualTo(TrainingActivityReportStatus.PENDING);
        verify(fixture.reportRepository).saveAndFlush(fixture.job.getReport());
    }

    @Test
    void af4_exhaustedFailureLeavesSubmittedAssignmentAndReportFailed() {
        var fixture = reportFixture();
        fixture.job.setAttemptCount(3);
        fixture.job.setMaxAttempts(3);
        when(fixture.jobRepository.fenceFailure(eq(fixture.job.getId()),
                eq(TrainingActivityAiJobType.FINAL_REPORT), eq(TrainingActivityAiJobStatus.RUNNING),
                eq(TrainingActivityAiJobStatus.FAILED), eq(4), any(), eq("INVALID_OUTPUT"), eq(1L), any()))
                .thenReturn(1);

        var outcome = fixture.service.applyFinalReportFailure(fixture.job.getId(), 4, "INVALID_OUTPUT");

        assertThat(outcome.terminal()).isTrue();
        assertThat(fixture.job.getAssignment().getStatus()).isEqualTo(TrainingActivityAssignmentStatus.SUBMITTED);
        assertThat(fixture.job.getReport().getStatus()).isEqualTo(TrainingActivityReportStatus.FAILED);
    }

    @Test
    void af5_duplicateRetryDoesNotMoveFailedReportWithoutAJob() {
        var fixture = reportFixture();
        fixture.job.getReport().setStatus(TrainingActivityReportStatus.FAILED);
        when(fixture.assignmentRepository.findLockedWithTrainingActivityById(fixture.job.getAssignment().getId()))
                .thenReturn(Optional.of(fixture.job.getAssignment()));
        when(fixture.reportRepository.findByAssignment_Id(fixture.job.getAssignment().getId()))
                .thenReturn(Optional.of(fixture.job.getReport()));
        when(fixture.jobRepository.findTopByAssignment_IdAndJobTypeOrderByGenerationDesc(
                fixture.job.getAssignment().getId(), TrainingActivityAiJobType.FINAL_REPORT))
                .thenReturn(Optional.of(fixture.job));
        when(fixture.jobRepository.insertFinalReportRetryIfAbsent(any(), anyInt(), any(), any(), any(), anyLong(),
                anyString(), anyInt(), anyInt(), any(), any(), any())).thenReturn(0);

        assertThat(fixture.service.retryFailedFinalReport(fixture.job.getAssignment().getId())).isFalse();
        assertThat(fixture.job.getReport().getStatus()).isEqualTo(TrainingActivityReportStatus.FAILED);
        verify(fixture.reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void af4_retryCompletesTheSameLogicalReport() {
        var fixture = reportFixture();
        var reportId = fixture.job.getReport().getId();
        fixture.job.getReport().setStatus(TrainingActivityReportStatus.FAILED);
        when(fixture.assignmentRepository.findLockedWithTrainingActivityById(fixture.job.getAssignment().getId()))
                .thenReturn(Optional.of(fixture.job.getAssignment()));
        when(fixture.reportRepository.findByAssignment_Id(fixture.job.getAssignment().getId()))
                .thenReturn(Optional.of(fixture.job.getReport()));
        when(fixture.jobRepository.findTopByAssignment_IdAndJobTypeOrderByGenerationDesc(
                fixture.job.getAssignment().getId(), TrainingActivityAiJobType.FINAL_REPORT))
                .thenReturn(Optional.of(fixture.job));
        when(fixture.jobRepository.insertFinalReportRetryIfAbsent(any(), anyInt(), any(), any(), any(), anyLong(),
                anyString(), anyInt(), anyInt(), any(), any(), any())).thenReturn(1);

        assertThat(fixture.service.retryFailedFinalReport(fixture.job.getAssignment().getId())).isTrue();
        assertThat(fixture.job.getReport().getId()).isEqualTo(reportId);
        assertThat(fixture.job.getReport().getStatus()).isEqualTo(TrainingActivityReportStatus.PENDING);

        fixture.job.getReport().setStatus(TrainingActivityReportStatus.GENERATING);
        fixture.job.getReport().setVersion(2);
        fixture.job.setInputVersion(2);
        when(fixture.jobRepository.fenceSuccess(eq(fixture.job.getId()), eq(TrainingActivityAiJobType.FINAL_REPORT),
                eq(TrainingActivityAiJobStatus.RUNNING), eq(TrainingActivityAiJobStatus.SUCCEEDED), eq(4), any())).thenReturn(1);

        assertThat(fixture.service.applyFinalReportSuccess(fixture.job.getId(), 4, weakCandidate("respuesta limitada"))).isTrue();
        assertThat(fixture.job.getReport().getId()).isEqualTo(reportId);
        assertThat(fixture.job.getReport().getStatus()).isEqualTo(TrainingActivityReportStatus.READY);
    }

    @Test
    void af4_retryUsesThePostResetVersionAndCanApplyExactlyOnce() {
        var fixture = reportFixture();
        var report = fixture.job.getReport();
        report.setStatus(TrainingActivityReportStatus.FAILED);
        report.setVersion(6);
        when(fixture.assignmentRepository.findLockedWithTrainingActivityById(fixture.job.getAssignment().getId()))
                .thenReturn(Optional.of(fixture.job.getAssignment()));
        when(fixture.assignmentRepository.findWithTrainingActivityById(fixture.job.getAssignment().getId()))
                .thenReturn(Optional.of(fixture.job.getAssignment()));
        when(fixture.reportRepository.findByAssignment_Id(fixture.job.getAssignment().getId())).thenReturn(Optional.of(report));
        when(fixture.jobRepository.findTopByAssignment_IdAndJobTypeOrderByGenerationDesc(
                fixture.job.getAssignment().getId(), TrainingActivityAiJobType.FINAL_REPORT)).thenReturn(Optional.of(fixture.job));
        when(fixture.jobRepository.insertFinalReportRetryIfAbsent(any(), anyInt(), any(), any(), any(), anyLong(), anyString(),
                anyInt(), anyInt(), any(), any(), any())).thenReturn(1);

        assertThat(fixture.service.retryFailedFinalReport(fixture.job.getAssignment().getId())).isTrue();
        verify(fixture.jobRepository).insertFinalReportRetryIfAbsent(any(), anyInt(), any(), any(), any(), eq(8L), anyString(),
                anyInt(), anyInt(), any(), any(), any());

        report.setVersion(7); // The persisted PENDING reset increments the optimistic version once.
        var retryJob = new TrainingActivityAiJob();
        retryJob.setId(UUID.randomUUID());
        retryJob.setJobType(TrainingActivityAiJobType.FINAL_REPORT);
        retryJob.setAssignment(fixture.job.getAssignment());
        retryJob.setReport(report);
        retryJob.setStatus(TrainingActivityAiJobStatus.RUNNING);
        retryJob.setGeneration(5);
        retryJob.setInputVersion(8);
        retryJob.setLeaseUntil(Instant.now().plusSeconds(30));
        when(fixture.jobRepository.findById(retryJob.getId())).thenReturn(Optional.of(retryJob));
        when(fixture.jobRepository.claimNext(any(), any())).thenReturn(Optional.of(retryJob));

        assertThat(fixture.service.claimNextWork(Instant.now(), Instant.now().plusSeconds(30)).finalReportWork()).isNotNull();
        assertThat(report.getStatus()).isEqualTo(TrainingActivityReportStatus.GENERATING);

        report.setVersion(8); // The claim's persisted GENERATING transition increments the version once more.
        when(fixture.jobRepository.fenceSuccess(eq(retryJob.getId()), eq(TrainingActivityAiJobType.FINAL_REPORT),
                eq(TrainingActivityAiJobStatus.RUNNING), eq(TrainingActivityAiJobStatus.SUCCEEDED), eq(5), any())).thenReturn(1);

        assertThat(fixture.service.applyFinalReportSuccess(retryJob.getId(), 5, weakCandidate("respuesta limitada"))).isTrue();
        assertThat(report.getStatus()).isEqualTo(TrainingActivityReportStatus.READY);
    }

    @Test
    void af9_closedActivityStillProjectsImmutableSubmittedReportAndOrderedTurns() {
        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        var reportRepository = mock(TrainingActivityReportRepository.class);
        var turnRepository = mock(TrainingActivityTurnRepository.class);
        var groupClassId = UUID.randomUUID();
        var assignment = submittedAssignment(groupClassId);
        assignment.getTrainingActivity().setStatus(
                com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus.CLOSED);
        var report = new TrainingActivityReport();
        report.setStatus(TrainingActivityReportStatus.READY);
        report.setSummary("Historical evidence summary.");
        var turn = new TrainingActivityTurn();
        turn.setSequenceNumber(1);
        turn.setQuestionText("Historical question");
        turn.setAnswerText("Historical answer");
        when(assignmentRepository.findWithTrainingActivityById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(reportRepository.findByAssignment_Id(assignment.getId())).thenReturn(Optional.of(report));
        when(turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(assignment.getId())).thenReturn(List.of(turn));
        var contextResolver = mock(ActiveAcademicContextResolver.class);
        when(contextResolver.requireCurrent()).thenReturn(context(groupClassId, GroupClassMemberKind.PROFESSOR));
        var service = new TrainingActivityReportProjectionService(assignmentRepository, reportRepository, turnRepository,
                contextResolver, mock(TrainingTutorJobService.class));

        var projection = service.getForCurrentReviewer(assignment.getId());

        assertThat(projection.status()).isEqualTo(TrainingActivityReportStatus.READY);
        assertThat(projection.turns()).singleElement().satisfies(value ->
                assertThat(value.answerText()).isEqualTo("Historical answer"));
    }

    private static TrainingActivityReportProjectionService projectionService(
            TrainingActivityAssignmentRepository assignmentRepository, ActiveAcademicContextResolver contextResolver) {
        return new TrainingActivityReportProjectionService(assignmentRepository, mock(TrainingActivityReportRepository.class),
                mock(TrainingActivityTurnRepository.class), contextResolver, mock(TrainingTutorJobService.class));
    }

    private static ActiveAcademicContext context(UUID groupClassId, GroupClassMemberKind kind) {
        return new ActiveAcademicContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), groupClassId, kind);
    }

    private static ReportFixture reportFixture() {
        var jobRepository = mock(TrainingActivityAiJobRepository.class);
        var assignmentRepository = mock(TrainingActivityAssignmentRepository.class);
        var turnRepository = mock(TrainingActivityTurnRepository.class);
        var reportRepository = mock(TrainingActivityReportRepository.class);
        var assignment = submittedAssignment(UUID.randomUUID());
        assignment.setEvidenceStatus(EvidenceStatus.WEAK_EVIDENCE);
        var report = new TrainingActivityReport();
        report.setId(UUID.randomUUID());
        report.setStatus(TrainingActivityReportStatus.GENERATING);
        report.setVersion(1);
        var job = new TrainingActivityAiJob();
        job.setId(UUID.randomUUID());
        job.setJobType(TrainingActivityAiJobType.FINAL_REPORT);
        job.setAssignment(assignment);
        job.setReport(report);
        job.setStatus(TrainingActivityAiJobStatus.RUNNING);
        job.setGeneration(4);
        job.setInputVersion(1);
        job.setLeaseUntil(Instant.now().plusSeconds(30));
        var turn = new TrainingActivityTurn();
        turn.setSequenceNumber(1);
        turn.setQuestionText("¿Qué observaste?");
        turn.setAnswerText("respuesta limitada");
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(assignmentRepository.findLockedWithTrainingActivityById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(assignment.getId())).thenReturn(List.of(turn));
        var service = new TrainingTutorJobService(jobRepository, assignmentRepository, turnRepository, reportRepository,
                mock(TrainingAssignmentTutorService.class), new SafeBrowserAssignmentStateBus());
        return new ReportFixture(service, jobRepository, assignmentRepository, turnRepository, reportRepository, job, turn);
    }

    private static ReportFixture noEvidenceReportFixture() {
        var fixture = reportFixture();
        when(fixture.turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(fixture.job.getAssignment().getId()))
                .thenReturn(List.of());
        return fixture;
    }

    private static TrainingActivityAssignment submittedAssignment(UUID groupClassId) {
        var groupClass = new GroupClass();
        groupClass.setId(groupClassId);
        var activity = new TrainingActivity();
        activity.setId(UUID.randomUUID());
        activity.setGroupClass(groupClass);
        var assignment = new TrainingActivityAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTrainingActivity(activity);
        var groupClassMember = new GroupClassMember();
        groupClassMember.setId(UUID.randomUUID());
        assignment.setGroupClassMember(groupClassMember);
        assignment.setStatus(TrainingActivityAssignmentStatus.SUBMITTED);
        return assignment;
    }

    private static FinalReportCandidate weakCandidate(String observation) {
        return weakCandidate(observation, List.of(reference(1, null, "respuesta limitada")));
    }

    private static FinalReportCandidate weakCandidate(
            String observation, List<FinalReportCandidate.EvidenceReference> references) {
        return new FinalReportCandidate(
                EvidenceStatus.WEAK_EVIDENCE,
                "La evidencia es limitada y no permite una conclusión sólida.",
                List.of(),
                List.of(new FinalReportCandidate.ReportFinding(observation, references)),
                List.of(new FinalReportCandidate.ReportFinding(observation, references)),
                List.of("Solicitar una explicación concreta."));
    }

    private static FinalReportCandidate noEvidenceCandidate(String summary) {
        return new FinalReportCandidate(EvidenceStatus.NO_EVIDENCE, summary, List.of(), List.of(), List.of(),
                List.of("Solicitar una respuesta explicada para obtener evidencia."));
    }

    private static FinalReportCandidate.EvidenceReference reference(
            int turnSequence, String questionExcerpt, String answerExcerpt) {
        return new FinalReportCandidate.EvidenceReference(turnSequence, questionExcerpt, answerExcerpt);
    }

    private static void assertValidationFailure(
            ReportFixture fixture, FinalReportCandidate candidate, Reason expectedReason) {
        var exception = catchThrowableOfType(
                () -> fixture.service.applyFinalReportSuccess(fixture.job.getId(), 4, candidate),
                FinalReportCandidateValidationException.class);

        assertThat(exception)
                .hasMessage("Final report candidate failed canonical evidence validation.")
                .hasNoCause();
        assertThat(exception.reason()).isEqualTo(expectedReason);
    }

    private record ReportFixture(TrainingTutorJobService service, TrainingActivityAiJobRepository jobRepository,
                                  TrainingActivityAssignmentRepository assignmentRepository,
                                  TrainingActivityTurnRepository turnRepository,
                                  TrainingActivityReportRepository reportRepository, TrainingActivityAiJob job,
                                 TrainingActivityTurn turn) {}
}
