package com.wornux.services.training_activity;

import java.time.Instant;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wornux.data.entities.training_activity.TrainingActivityAiJob;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobStatus;
import com.wornux.data.entities.training_activity.TrainingActivityAiJobType;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;
import com.wornux.data.entities.training_activity.EvidenceStatus;
import com.wornux.data.entities.training_activity.TrainingActivityReport;
import com.wornux.data.entities.training_activity.TrainingActivityReportEvidenceReference;
import com.wornux.data.entities.training_activity.TrainingActivityReportFinding;
import com.wornux.data.entities.training_activity.TrainingActivityReportStatus;
import com.wornux.data.entities.training_activity.TrainingActivityTurn;
import com.wornux.data.repositories.training_activity.TrainingActivityAiJobRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityReportRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityTurnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Durable UC-007 orchestration. Each public persistence method is intentionally short. */
@Service
public class TrainingTutorJobService {
    private static final int REPORT_PRIORITY = 200;
    private static final int MAX_ATTEMPTS = 3;
    private final TrainingActivityAiJobRepository jobRepository;
    private final TrainingActivityAssignmentRepository assignmentRepository;
    private final TrainingActivityTurnRepository turnRepository;
    private final TrainingActivityReportRepository reportRepository;
    private final TrainingAssignmentTutorService tutorService;
    private final SafeBrowserAssignmentStateBus assignmentStateBus;

    public TrainingTutorJobService(
            TrainingActivityAiJobRepository jobRepository,
            TrainingActivityAssignmentRepository assignmentRepository,
            TrainingActivityTurnRepository turnRepository,
            TrainingActivityReportRepository reportRepository,
            TrainingAssignmentTutorService tutorService,
            SafeBrowserAssignmentStateBus assignmentStateBus) {
        this.jobRepository = jobRepository;
        this.assignmentRepository = assignmentRepository;
        this.turnRepository = turnRepository;
        this.reportRepository = reportRepository;
        this.tutorService = tutorService;
        this.assignmentStateBus = assignmentStateBus;
    }

    @Transactional
    public ClaimedWork claimNextWork(Instant now, Instant leaseUntil) {
        var job = jobRepository.claimNext(now, leaseUntil).orElse(null);
        if (job == null) {
            return null;
        }
        if (job.getJobType() == TrainingActivityAiJobType.FINAL_REPORT) {
            return claimedFinalReportWork(job, now);
        }
        if (job.getJobType() == TrainingActivityAiJobType.INSTRUCTION_REVIEW) {
            return new ClaimedWork(job, null, null);
        }
        var assignment = assignmentRepository.findWithTrainingActivityById(job.getAssignment().getId()).orElseThrow();
        var turns = turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(assignment.getId());
        var transcript = turns.stream().filter(turn -> turn.getAnswerText() != null)
                .map(turn -> new TrainingAssignmentEvaluationService.EvaluationExchange(
                        turn.getQuestionText(), turn.getAnswerText())).toList();
        var work = new TutorWork(job.getId(), job.getJobType(), assignment,
                job.getTurn() == null ? null : job.getTurn().getId(), job.getInputVersion(), job.getGeneration(),
                job.getTurn() == null ? "" : job.getTurn().getAnswerText(), transcript, turns.size(),
                job.getTurn() == null ? null : job.getTurn().getQuestionText());
        return new ClaimedWork(job, work, null);
    }

    private ClaimedWork claimedFinalReportWork(TrainingActivityAiJob job, Instant now) {
        if (job.getReport() == null || job.getAssignment() == null) {
            markStale(job);
            return null;
        }
        var assignment = assignmentRepository.findWithTrainingActivityById(job.getAssignment().getId()).orElseThrow();
        var report = reportRepository.findById(job.getReport().getId()).orElseThrow();
        var recoveringExpiredLease = report.getStatus() == TrainingActivityReportStatus.GENERATING;
        if (assignment.getStatus() != TrainingActivityAssignmentStatus.SUBMITTED
                || report.getStatus() == TrainingActivityReportStatus.READY
                || report.getStatus() == TrainingActivityReportStatus.FAILED
                || (!recoveringExpiredLease && report.getVersion() + 1 != job.getInputVersion())
                || (recoveringExpiredLease && report.getVersion() != job.getInputVersion())) {
            if (recoveringExpiredLease) {
                report.setStatus(TrainingActivityReportStatus.PENDING);
                report.setUpdatedAt(now);
                reportRepository.saveAndFlush(report);
            }
            markStale(job);
            return null;
        }
        if (recoveringExpiredLease) {
            job.setInputVersion(report.getVersion() + 1);
        }
        report.setStatus(TrainingActivityReportStatus.GENERATING);
        report.setAttemptCount(job.getAttemptCount());
        report.setLastErrorCode(null);
        report.setUpdatedAt(now);
        reportRepository.saveAndFlush(report);
        var turns = turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(assignment.getId()).stream()
                .filter(turn -> turn.getAnswerText() != null)
                .map(turn -> new TrainingAssignmentTutorService.ReportTurn(
                        turn.getSequenceNumber(), turn.getQuestionText(), turn.getAnswerText())).toList();
        var work = new FinalReportWork(job.getId(), job.getGeneration(), job.getInputVersion(), assignment, report.getId(), turns);
        return new ClaimedWork(job, null, work);
    }

    /** Called by the shared bounded worker outside any transaction. */
    public AdaptiveTutorDecision callModel(TutorWork work) {
        return work.type() == TrainingActivityAiJobType.FIRST_QUESTION
                ? tutorService.firstDecision(work.assignment(), work.questionCount(), work.currentQuestion())
                : tutorService.nextDecision(work.assignment(), work.latestAnswer(), work.transcript(), work.questionCount(), work.currentQuestion());
    }

    /** Called by the shared bounded worker outside any transaction. */
    public FinalReportCandidate callFinalReportModel(FinalReportWork work) {
        return tutorService.generateFinalReport(work.assignment(), work.turns(), work.assignment().getEvidenceStatus());
    }

    @Transactional
    public boolean applySuccess(UUID jobId, int ownershipGeneration, AdaptiveTutorDecision decision) {
        var job = jobRepository.findById(jobId).orElseThrow();
        if (!canApply(job, ownershipGeneration)) {
            return false;
        }
        var assignment = assignmentRepository.findLockedWithTrainingActivityById(job.getAssignment().getId()).orElseThrow();
        validateDecision(job.getJobType(), decision);
        var now = Instant.now();
        TrainingActivityTurn answeredTurn = null;
        if (job.getJobType() == TrainingActivityAiJobType.FIRST_QUESTION) {
            if (assignment.getStatus() != TrainingActivityAssignmentStatus.STARTING) {
                markStale(job);
                return false;
            }
        }
        else {
            if (assignment.getStatus() != TrainingActivityAssignmentStatus.WAITING_FOR_TUTOR || job.getTurn() == null) {
                markStale(job);
                return false;
            }
            answeredTurn = turnRepository.findById(job.getTurn().getId()).orElseThrow();
            if (answeredTurn.getAnswerText() == null || answeredTurn.getAnswerText().isBlank()) {
                markStale(job);
                return false;
            }
        }
        if (jobRepository.fenceSuccess(jobId, job.getJobType(), TrainingActivityAiJobStatus.RUNNING,
                TrainingActivityAiJobStatus.SUCCEEDED, ownershipGeneration, now) == 0) {
            return false;
        }
        if (job.getJobType() == TrainingActivityAiJobType.FIRST_QUESTION) {
            turnRepository.save(newQuestion(assignment, 1, decision.questionText(), now));
            assignment.setStatus(TrainingActivityAssignmentStatus.WAITING_FOR_ANSWER);
        }
        else {
            applyDecision(answeredTurn, decision, now);
            if (decision.type() == TutorDecisionType.QUESTION) {
                turnRepository.save(newQuestion(assignment, answeredTurn.getSequenceNumber() + 1, decision.questionText(), now));
                assignment.setStatus(TrainingActivityAssignmentStatus.WAITING_FOR_ANSWER);
            }
            else {
                complete(assignment, decision, now);
            }
        }
        assignment.setUpdatedAt(now);
        assignmentRepository.save(assignment);
        closeActivityWhenAllAssignmentsAreTerminal(assignment, now);
        publishAfterCommit(assignment);
        return true;
    }

    @Transactional
    public TutorFailureOutcome applyFailure(UUID jobId, int ownershipGeneration, String failureCode) {
        var job = jobRepository.findById(jobId).orElseThrow();
        if (!canApply(job, ownershipGeneration)) {
            return TutorFailureOutcome.staleOwnership();
        }
        var retry = job.getAttemptCount() < job.getMaxAttempts();
        var now = Instant.now();
        if (jobRepository.fenceFailure(jobId, job.getJobType(), TrainingActivityAiJobStatus.RUNNING,
                retry ? TrainingActivityAiJobStatus.RETRYABLE : TrainingActivityAiJobStatus.FAILED,
                ownershipGeneration, now.plusSeconds(retry ? Math.max(5L, job.getAttemptCount() * 5L) : 0),
                failureCode, job.getInputVersion(), now) == 0) {
            return TutorFailureOutcome.staleOwnership();
        }
        if (retry) {
            return TutorFailureOutcome.retry();
        }
        transitionToTemporaryError(job);
        return TutorFailureOutcome.terminalFailure();
    }

    @Transactional
    public TutorFailureOutcome applyFinalReportFailure(UUID jobId, int ownershipGeneration, String failureCode) {
        var job = jobRepository.findById(jobId).orElseThrow();
        if (job.getJobType() != TrainingActivityAiJobType.FINAL_REPORT) {
            return TutorFailureOutcome.staleOwnership();
        }
        var report = reportRepository.findById(job.getReport().getId()).orElseThrow();
        if (report.getStatus() != TrainingActivityReportStatus.GENERATING || report.getVersion() != job.getInputVersion()) {
            return TutorFailureOutcome.staleOwnership();
        }
        var now = Instant.now();
        var retry = job.getAttemptCount() < job.getMaxAttempts();
        var targetStatus = retry ? TrainingActivityAiJobStatus.RETRYABLE : TrainingActivityAiJobStatus.FAILED;
        var availableAt = now.plusSeconds(retry ? Math.max(5L, job.getAttemptCount() * 5L) : 0);
        if (jobRepository.fenceFailure(jobId, TrainingActivityAiJobType.FINAL_REPORT,
                TrainingActivityAiJobStatus.RUNNING, targetStatus, ownershipGeneration, availableAt, failureCode,
                job.getInputVersion() + (retry ? 2 : 0), now) == 0) {
            return TutorFailureOutcome.staleOwnership();
        }
        report.setStatus(retry ? TrainingActivityReportStatus.PENDING : TrainingActivityReportStatus.FAILED);
        report.setAttemptCount(job.getAttemptCount());
        report.setLastErrorCode(failureCode);
        report.setCompletedAt(retry ? null : now);
        report.setUpdatedAt(now);
        reportRepository.saveAndFlush(report);
        var assignment = assignmentRepository.findLockedWithTrainingActivityById(job.getAssignment().getId()).orElseThrow();
        publishAfterCommit(assignment);
        return retry ? TutorFailureOutcome.retry() : TutorFailureOutcome.terminalFailure();
    }

    @Transactional
    public boolean applyFinalReportSuccess(UUID jobId, int ownershipGeneration, FinalReportCandidate candidate) {
        var job = jobRepository.findById(jobId).orElseThrow();
        if (job.getJobType() != TrainingActivityAiJobType.FINAL_REPORT) {
            return false;
        }
        var assignment = assignmentRepository.findLockedWithTrainingActivityById(job.getAssignment().getId()).orElseThrow();
        var report = reportRepository.findById(job.getReport().getId()).orElseThrow();
        if (assignment.getStatus() != TrainingActivityAssignmentStatus.SUBMITTED
                || report.getStatus() != TrainingActivityReportStatus.GENERATING
                || report.getVersion() != job.getInputVersion()) {
            return false;
        }
        var turns = turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(assignment.getId());
        validateCandidateAgainstCanonicalTranscript(candidate, assignment, turns);
        var now = Instant.now();
        if (jobRepository.fenceSuccess(jobId, TrainingActivityAiJobType.FINAL_REPORT,
                TrainingActivityAiJobStatus.RUNNING, TrainingActivityAiJobStatus.SUCCEEDED, ownershipGeneration, now) == 0) {
            return false;
        }
        report.setStatus(TrainingActivityReportStatus.READY);
        report.setEvidenceStatus(candidate.evidenceStatus());
        report.setSummary(candidate.summary().trim());
        report.setStrengths(toFindings(candidate.strengths()));
        report.setWeaknesses(toFindings(candidate.weaknesses()));
        report.setObservations(toFindings(candidate.observations()));
        report.setRecommendations(candidate.recommendations().stream().map(String::trim).toList());
        report.setAttemptCount(job.getAttemptCount());
        report.setLastErrorCode(null);
        report.setCompletedAt(now);
        report.setUpdatedAt(now);
        reportRepository.saveAndFlush(report);
        publishAfterCommit(assignment);
        return true;
    }

    @Transactional
    public boolean retryFailedFinalReport(UUID assignmentId) {
        var assignment = assignmentRepository.findLockedWithTrainingActivityById(assignmentId).orElseThrow();
        var report = reportRepository.findByAssignment_Id(assignmentId).orElseThrow();
        if (assignment.getStatus() != TrainingActivityAssignmentStatus.SUBMITTED
                || report.getStatus() != TrainingActivityReportStatus.FAILED) {
            return false;
        }
        var priorJob = jobRepository.findTopByAssignment_IdAndJobTypeOrderByGenerationDesc(
                assignmentId, TrainingActivityAiJobType.FINAL_REPORT).orElseThrow();
        var now = Instant.now();
        // saveAndFlush below advances the reset report to its next optimistic version; claiming then advances it once more.
        if (jobRepository.insertFinalReportRetryIfAbsent(UUID.randomUUID(), REPORT_PRIORITY,
                assignment.getTrainingActivity().getId(), assignment.getId(), report.getId(), report.getVersion() + 2,
                "report:" + assignment.getId(), priorJob.getGeneration() + 1, MAX_ATTEMPTS, now, now, now) == 0) {
            return false;
        }
        report.setStatus(TrainingActivityReportStatus.PENDING);
        report.setAttemptCount(0);
        report.setLastErrorCode(null);
        report.setRequestedAt(now);
        report.setCompletedAt(null);
        report.setUpdatedAt(now);
        reportRepository.saveAndFlush(report);
        return true;
    }

    @Transactional
    public boolean retryTemporaryFailure(UUID assignmentId) {
        var assignment = assignmentRepository.findLockedWithTrainingActivityById(assignmentId).orElseThrow();
        if (assignment.getStatus() != TrainingActivityAssignmentStatus.TEMPORARILY_UNAVAILABLE) {
            return false;
        }
        var job = jobRepository.findFirstByAssignment_IdAndJobTypeInOrderByUpdatedAtDesc(assignmentId,
                List.of(TrainingActivityAiJobType.FIRST_QUESTION, TrainingActivityAiJobType.NEXT_DECISION)).orElseThrow();
        var now = Instant.now();
        if (job.getJobType() == TrainingActivityAiJobType.FIRST_QUESTION
                && turnRepository.findFirstByAssignment_IdAndAnswerTextIsNullOrderBySequenceNumberDesc(assignmentId).isPresent()) {
            job.setStatus(TrainingActivityAiJobStatus.SUCCEEDED);
            job.setLeaseUntil(null);
            job.setLastErrorCode(null);
            job.setUpdatedAt(now);
            assignment.setStatus(TrainingActivityAssignmentStatus.WAITING_FOR_ANSWER);
            assignment.setUpdatedAt(now);
            assignmentRepository.save(assignment);
            publishAfterCommit(assignment);
            return true;
        }
        job.setStatus(TrainingActivityAiJobStatus.PENDING);
        job.setAttemptCount(0);
        job.setAvailableAt(now);
        job.setLeaseUntil(null);
        job.setLastErrorCode(null);
        job.setUpdatedAt(now);
        job.setInputVersion(assignment.getVersion() + 1);
        assignment.setStatus(job.getJobType() == TrainingActivityAiJobType.FIRST_QUESTION
                ? TrainingActivityAssignmentStatus.STARTING : TrainingActivityAssignmentStatus.WAITING_FOR_TUTOR);
        assignment.setUpdatedAt(now);
        assignmentRepository.save(assignment);
        publishAfterCommit(assignment);
        return true;
    }

    private void reconcileExpiredTutorClaims(Instant now) {
        jobRepository.findExpiredAtAttemptLimit(
                        List.of(TrainingActivityAiJobType.FIRST_QUESTION, TrainingActivityAiJobType.NEXT_DECISION),
                        TrainingActivityAiJobStatus.RUNNING, now)
                .forEach(job -> {
                    job.setStatus(TrainingActivityAiJobStatus.FAILED);
                    job.setLeaseUntil(null);
                    job.setLastErrorCode("LEASE_EXPIRED");
                    job.setUpdatedAt(now);
                    transitionToTemporaryError(job);
                });
    }

    private void reconcileExpiredReportClaims(Instant now) {
        jobRepository.findExpiredAtAttemptLimit(
                        List.of(TrainingActivityAiJobType.FINAL_REPORT), TrainingActivityAiJobStatus.RUNNING, now)
                .forEach(job -> {
                    var report = reportRepository.findById(job.getReport().getId()).orElse(null);
                    job.setStatus(TrainingActivityAiJobStatus.FAILED);
                    job.setLeaseUntil(null);
                    job.setLastErrorCode("LEASE_EXPIRED");
                    job.setUpdatedAt(now);
                    if (report != null && report.getStatus() == TrainingActivityReportStatus.GENERATING) {
                        report.setStatus(TrainingActivityReportStatus.FAILED);
                        report.setAttemptCount(job.getAttemptCount());
                        report.setLastErrorCode("LEASE_EXPIRED");
                        report.setCompletedAt(now);
                        report.setUpdatedAt(now);
                        reportRepository.save(report);
                        var assignment = assignmentRepository.findLockedWithTrainingActivityById(job.getAssignment().getId())
                                .orElseThrow();
                        publishAfterCommit(assignment);
                    }
                });
    }

    private boolean canApply(TrainingActivityAiJob job, int ownershipGeneration) {
        return job.getStatus() == TrainingActivityAiJobStatus.RUNNING && job.getLeaseUntil() != null
                && !job.getLeaseUntil().isBefore(Instant.now()) && job.getGeneration() == ownershipGeneration;
    }

    private void markStale(TrainingActivityAiJob job) {
        job.setStatus(TrainingActivityAiJobStatus.SUCCEEDED);
        job.setLeaseUntil(null);
        job.setLastErrorCode("STALE_RESULT");
        job.setUpdatedAt(Instant.now());
    }

    public record ClaimedWork(TrainingActivityAiJob job, TutorWork tutorWork, FinalReportWork finalReportWork) { }

    private void transitionToTemporaryError(TrainingActivityAiJob job) {
        var assignment = assignmentRepository.findLockedWithTrainingActivityById(job.getAssignment().getId()).orElseThrow();
        if (assignment.getStatus() == TrainingActivityAssignmentStatus.STARTING
                || assignment.getStatus() == TrainingActivityAssignmentStatus.WAITING_FOR_TUTOR) {
            assignment.setStatus(TrainingActivityAssignmentStatus.TEMPORARILY_UNAVAILABLE);
            assignment.setUpdatedAt(Instant.now());
            assignmentRepository.save(assignment);
            publishAfterCommit(assignment);
        }
    }

    private TrainingActivityTurn newQuestion(TrainingActivityAssignment assignment, int sequence, String question, Instant now) {
        var turn = new TrainingActivityTurn();
        turn.setId(UUID.randomUUID());
        turn.setAssignment(assignment);
        turn.setSequenceNumber(sequence);
        turn.setQuestionText(question.trim());
        turn.setQuestionCreatedAt(now);
        turn.setCreatedAt(now);
        turn.setUpdatedAt(now);
        return turn;
    }

    private void applyDecision(TrainingActivityTurn turn, AdaptiveTutorDecision decision, Instant now) {
        turn.setDecisionType(decision.type());
        turn.setAnswerQuality(decision.answerQuality());
        turn.setEvidenceStatus(decision.evidenceStatus());
        turn.setCoverageStatus(decision.coverageStatus());
        turn.setPedagogicalMove(decision.pedagogicalMove());
        turn.setDecisionMetadata(validatedMetadata(decision));
        turn.setUpdatedAt(now);
        turnRepository.save(turn);
    }

    private void complete(TrainingActivityAssignment assignment, AdaptiveTutorDecision decision, Instant now) {
        assignment.setStatus(TrainingActivityAssignmentStatus.SUBMITTED);
        assignment.setSubmittedAt(now);
        assignment.setEvidenceStatus(decision.evidenceStatus());
        assignment.setCompletionReason(decision.type().name());
        reportRepository.insertPendingIfAbsent(UUID.randomUUID(), assignment.getId(), decision.evidenceStatus().name(),
                tutorService.currentModelName(), tutorService.promptVersion(), now);
        var report = reportRepository.findByAssignment_Id(assignment.getId())
                .orElseThrow(() -> new IllegalStateException("The pending report was not persisted."));
        jobRepository.insertTutorJobIfAbsent(UUID.randomUUID(), TrainingActivityAiJobType.FINAL_REPORT.name(), REPORT_PRIORITY,
                assignment.getTrainingActivity().getId(), assignment.getId(), null, report.getId(), report.getVersion() + 1,
                "report:" + assignment.getId(), MAX_ATTEMPTS, now, now, now);
    }

    private void closeActivityWhenAllAssignmentsAreTerminal(TrainingActivityAssignment assignment, Instant now) {
        if (!assignment.getStatus().isTerminal()) {
            return;
        }
        var activity = assignment.getTrainingActivity();
        if (activity.getStatus() != com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus.PUBLISHED) {
            return;
        }
        var terminal = List.of(TrainingActivityAssignmentStatus.SUBMITTED, TrainingActivityAssignmentStatus.SKIPPED,
                TrainingActivityAssignmentStatus.EXPIRED, TrainingActivityAssignmentStatus.EXCUSED);
        if (assignmentRepository.countNonTerminal(activity.getId(), terminal) == 0) {
            activity.setStatus(com.wornux.data.entities.training_activity.TrainingActivityLifecycleStatus.CLOSED);
            activity.setClosesAt(now);
            activity.setUpdatedAt(now);
        }
    }

    private void validateCandidateAgainstCanonicalTranscript(
            FinalReportCandidate candidate, TrainingActivityAssignment assignment, List<TrainingActivityTurn> turns) {
        if (candidate == null) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.NULL_CANDIDATE);
        }
        if (candidate.evidenceStatus() == null) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.MISSING_EVIDENCE_STATUS);
        }
        if (candidate.evidenceStatus() != assignment.getEvidenceStatus()) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.EVIDENCE_STATUS_MISMATCH);
        }
        if (invalidText(candidate.summary(), 2_000)) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.INVALID_SUMMARY);
        }
        if (candidate.strengths() == null || candidate.weaknesses() == null || candidate.observations() == null) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.MISSING_FINDING_COLLECTIONS);
        }
        if (candidate.recommendations() == null || candidate.recommendations().isEmpty()) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.MISSING_RECOMMENDATIONS);
        }
        if (candidate.recommendations().size() > 8) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.TOO_MANY_RECOMMENDATIONS);
        }
        if (candidate.evidenceStatus() == EvidenceStatus.NO_EVIDENCE) {
            if (!candidate.strengths().isEmpty() || !candidate.weaknesses().isEmpty() || !candidate.observations().isEmpty()
                    || !containsNoEvidenceConclusion(candidate.summary())) {
                throw validationFailure(FinalReportCandidateValidationException.Reason.NO_EVIDENCE_CONTRACT_MISMATCH);
            }
        }
        else {
            var answeredTurns = turns.stream().filter(turn -> turn.getAnswerText() != null && !turn.getAnswerText().isBlank())
                    .collect(java.util.stream.Collectors.toMap(TrainingActivityTurn::getSequenceNumber, turn -> turn));
            if (assignment.getEvidenceStatus() == EvidenceStatus.WEAK_EVIDENCE
                    && (!candidate.strengths().isEmpty() || !containsEvidenceLimitation(candidate.summary()))) {
                throw validationFailure(FinalReportCandidateValidationException.Reason.WEAK_EVIDENCE_CONTRACT_MISMATCH);
            }
            validateDiagnosticFindings(candidate.strengths(), answeredTurns);
            validateDiagnosticFindings(candidate.weaknesses(), answeredTurns);
            validateDiagnosticFindings(candidate.observations(), answeredTurns);
        }
        if (candidate.recommendations().stream().anyMatch(value -> invalidText(value, 800))) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.INVALID_RECOMMENDATION);
        }
    }

    private void validateDiagnosticFindings(
            List<FinalReportCandidate.ReportFinding> findings, Map<Integer, TrainingActivityTurn> answeredTurns) {
        if (findings.size() > 8 || findings.stream().anyMatch(finding -> finding == null || invalidText(finding.observation(), 800))) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.FINDING_LIMIT_EXCEEDED_OR_INVALID_TEXT);
        }
        for (var finding : findings) {
            if (finding.evidenceReferences() == null || finding.evidenceReferences().isEmpty()) {
                throw validationFailure(FinalReportCandidateValidationException.Reason.MISSING_EVIDENCE_REFERENCE);
            }
            for (var reference : finding.evidenceReferences()) {
                validateEvidenceReference(reference, answeredTurns);
            }
        }
    }

    private boolean containsEvidenceLimitation(String summary) {
        var normalized = summary == null ? "" : summary.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("limitad") || normalized.contains("insuficient") || normalized.contains("no permite")
                || normalized.contains("no hay evidencia") || normalized.contains("sin evidencia");
    }

    private boolean containsNoEvidenceConclusion(String summary) {
        var normalized = Normalizer.normalize(summary == null ? "" : summary, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("no hay evidencia") || normalized.contains("sin evidencia")
                || normalized.contains("evidencia insuficiente") || normalized.contains("evidencia es insuficiente")
                || normalized.contains("no permite concluir")
                || normalized.contains("no evidence") || normalized.contains("insufficient evidence")
                || normalized.contains("cannot conclude");
    }

    private void validateEvidenceReference(
            FinalReportCandidate.EvidenceReference reference, Map<Integer, TrainingActivityTurn> answeredTurns) {
        if (reference == null) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.MISSING_EVIDENCE_REFERENCE);
        }
        if (reference.turnSequence() == null || reference.turnSequence() < 1) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.INVALID_TURN_REFERENCE);
        }
        var turn = answeredTurns.get(reference.turnSequence());
        if (turn == null) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.INVALID_TURN_REFERENCE);
        }
        if (!matchesCanonicalExcerpt(reference.questionExcerpt(), turn.getQuestionText())) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.QUESTION_EXCERPT_MISMATCH);
        }
        if (!matchesCanonicalExcerpt(reference.answerExcerpt(), turn.getAnswerText())) {
            throw validationFailure(FinalReportCandidateValidationException.Reason.ANSWER_EXCERPT_MISMATCH);
        }
    }

    private FinalReportCandidateValidationException validationFailure(FinalReportCandidateValidationException.Reason reason) {
        return new FinalReportCandidateValidationException(reason);
    }

    private boolean matchesCanonicalExcerpt(String excerpt, String canonicalText) {
        if (excerpt == null) {
            return true;
        }
        var normalizedExcerpt = normalizeEvidenceExcerpt(excerpt);
        return !normalizedExcerpt.isBlank() && normalizeEvidenceExcerpt(canonicalText).contains(normalizedExcerpt);
    }

    private String normalizeEvidenceExcerpt(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC)
                .replaceAll("[\\s\\p{Z}]+", " ")
                .trim();
    }

    private boolean invalidText(String value, int maxLength) {
        var normalized = value == null ? "" : value.trim();
        var lowered = normalized.toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() || normalized.length() > maxLength || normalized.contains("<think>")
                || lowered.contains("chain-of-thought") || lowered.matches(".*\\b(grade|score|percentage|porcentaje|nota|aprobado|reprobado)\\b.*")
                || lowered.contains("complete_success") || lowered.contains("complete_insufficient_evidence")
                || lowered.contains("answerquality") || lowered.contains("evidencestatus") || lowered.contains("coveragestatus");
    }

    private List<TrainingActivityReportFinding> toFindings(List<FinalReportCandidate.ReportFinding> findings) {
        return findings.stream().map(finding -> new TrainingActivityReportFinding(
                finding.observation().trim(), finding.evidenceReferences().stream()
                        .map(reference -> new TrainingActivityReportEvidenceReference(reference.turnSequence()))
                        .toList())).toList();
    }

    private Map<String, Object> validatedMetadata(AdaptiveTutorDecision decision) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("type", decision.type().name());
        metadata.put("answerQuality", decision.answerQuality() == null ? null : decision.answerQuality().name());
        metadata.put("evidenceStatus", decision.evidenceStatus().name());
        metadata.put("coverageStatus", decision.coverageStatus().name());
        metadata.put("pedagogicalMove", decision.pedagogicalMove().name());
        metadata.put("coveredInstructionAspects", List.copyOf(decision.coveredInstructionAspects()));
        metadata.put("missingInstructionAspects", List.copyOf(decision.missingInstructionAspects()));
        metadata.put("reasonCode", decision.type().name());
        return metadata;
    }

    private void validateDecision(TrainingActivityAiJobType jobType, AdaptiveTutorDecision decision) {
        if (decision == null || decision.type() == null || decision.evidenceStatus() == null || decision.coverageStatus() == null
                || decision.pedagogicalMove() == null) {
            throw new IllegalArgumentException("The tutor returned an incomplete structured decision.");
        }
        var question = decision.questionText() == null ? "" : decision.questionText().trim();
        if (decision.type() == TutorDecisionType.QUESTION && (!question.matches(".*\\S.*") || !question.endsWith("?"))) {
            throw new IllegalArgumentException("A continuation decision requires exactly one Spanish question.");
        }
        if (decision.type() != TutorDecisionType.QUESTION && !question.isBlank()) {
            throw new IllegalArgumentException("Terminal tutor decisions cannot contain a student question.");
        }
        if (jobType == TrainingActivityAiJobType.FIRST_QUESTION && decision.type() != TutorDecisionType.QUESTION) {
            throw new IllegalArgumentException("The first tutor decision must contain a question.");
        }
    }

    private void publishAfterCommit(TrainingActivityAssignment assignment) {
        var notification = new SafeBrowserAssignmentStateBus.Notification(
                assignment.getTrainingActivity().getId(), assignment.getId(), assignment.getGroupClassMember().getId(),
                assignment.isSafeBrowserLocked(), false);
        // All mutation entry points are transactional; an uncommitted state must never reach browser subscribers.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                assignmentStateBus.publish(notification);
            }
        });
    }

    public record TutorWork(UUID jobId, TrainingActivityAiJobType type, TrainingActivityAssignment assignment,
                             UUID turnId, long inputVersion, int ownershipGeneration, String latestAnswer,
                             List<TrainingAssignmentEvaluationService.EvaluationExchange> transcript,
                             int questionCount, String currentQuestion) {}
    public record FinalReportWork(UUID jobId, int ownershipGeneration, long inputVersion,
                                  TrainingActivityAssignment assignment, UUID reportId,
                                  List<TrainingAssignmentTutorService.ReportTurn> turns) {}
    public record TutorFailureOutcome(boolean retryScheduled, boolean terminal, boolean stale) {
        static TutorFailureOutcome retry() { return new TutorFailureOutcome(true, false, false); }
        static TutorFailureOutcome terminalFailure() { return new TutorFailureOutcome(false, true, false); }
        static TutorFailureOutcome staleOwnership() { return new TutorFailureOutcome(false, false, true); }
    }
}
