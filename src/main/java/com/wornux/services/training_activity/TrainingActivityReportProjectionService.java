package com.wornux.services.training_activity;

import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.training_activity.TrainingActivityReport;
import com.wornux.data.entities.training_activity.TrainingActivityReportFinding;
import com.wornux.data.entities.training_activity.TrainingActivityReportStatus;
import com.wornux.data.repositories.training_activity.TrainingActivityAssignmentRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityReportRepository;
import com.wornux.data.repositories.training_activity.TrainingActivityTurnRepository;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.context.ActiveAcademicContextResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, authorized projection. Transcript content remains sourced from training_activity_turn. */
@Service
public class TrainingActivityReportProjectionService {
    private final TrainingActivityAssignmentRepository assignmentRepository;
    private final TrainingActivityReportRepository reportRepository;
    private final TrainingActivityTurnRepository turnRepository;
    private final ActiveAcademicContextResolver contextResolver;
    private final TrainingTutorJobService tutorJobService;

    public TrainingActivityReportProjectionService(
            TrainingActivityAssignmentRepository assignmentRepository,
            TrainingActivityReportRepository reportRepository,
            TrainingActivityTurnRepository turnRepository,
            ActiveAcademicContextResolver contextResolver,
            TrainingTutorJobService tutorJobService) {
        this.assignmentRepository = assignmentRepository;
        this.reportRepository = reportRepository;
        this.turnRepository = turnRepository;
        this.contextResolver = contextResolver;
        this.tutorJobService = tutorJobService;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_VIEW)
    public ReportProjection getForCurrentReviewer(UUID assignmentId) {
        var assignment = requireReviewableAssignment(assignmentId);
        if (assignment.getStatus() != com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus.SUBMITTED) {
            throw new IllegalStateException("The evaluation has not been submitted.");
        }
        var report = reportRepository.findByAssignment_Id(assignmentId).orElse(null);
        var turns = turnRepository.findByAssignment_IdOrderBySequenceNumberAsc(assignmentId).stream()
                .map(turn -> new TurnProjection(turn.getSequenceNumber(), turn.getQuestionText(), turn.getAnswerText()))
                .toList();
        return new ReportProjection(assignment, report == null ? TrainingActivityReportStatus.PENDING : report.getStatus(),
                report == null ? null : report.getEvidenceStatus(), report == null ? "" : report.getSummary(),
                report == null ? List.of() : safe(report.getStrengths()), report == null ? List.of() : safe(report.getWeaknesses()),
                report == null ? List.of() : safe(report.getObservations()), report == null ? List.of() : safeStrings(report.getRecommendations()),
                report == null ? null : report.getLastErrorCode(), turns);
    }

    @Transactional
    @RequiresPermission(AppPermission.TRAINING_ACTIVITY_VIEW)
    public boolean retryFailedReport(UUID assignmentId) {
        requireReviewableAssignment(assignmentId);
        return tutorJobService.retryFailedFinalReport(assignmentId);
    }

    private TrainingActivityAssignment requireReviewableAssignment(UUID assignmentId) {
        var context = contextResolver.requireCurrent();
        if (context.groupClassKind() != GroupClassMemberKind.PROFESSOR) {
            throw new SecurityException("Only an authorized professor can review formative reports.");
        }
        return assignmentRepository.findWithTrainingActivityById(assignmentId)
                .filter(value -> context.groupClassId().equals(value.getTrainingActivity().getGroupClass().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown training assignment " + assignmentId));
    }

    private static List<TrainingActivityReportFinding> safe(List<TrainingActivityReportFinding> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static List<String> safeStrings(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record ReportProjection(
            TrainingActivityAssignment assignment,
            TrainingActivityReportStatus status,
            com.wornux.data.entities.training_activity.EvidenceStatus evidenceStatus,
            String summary,
            List<TrainingActivityReportFinding> strengths,
            List<TrainingActivityReportFinding> weaknesses,
            List<TrainingActivityReportFinding> observations,
            List<String> recommendations,
            String failureCode,
            List<TurnProjection> turns) {}

    public record TurnProjection(int sequenceNumber, String questionText, String answerText) {}
}
