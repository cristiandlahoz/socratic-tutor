package com.wornux.services.training_activity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.training_activity.TrainingActivity;
import com.wornux.data.entities.training_activity.TrainingActivityAssignment;
import com.wornux.data.entities.training_activity.TrainingActivityAssignmentStatus;

/** Read model for an assignment whose conversation state is derived from durable turns. */
public record TrainingActivityAssignmentSnapshot(
        TrainingActivityAssignment assignment,
        List<TrainingAssignmentEvaluationService.EvaluationExchange> transcript,
        String currentQuestion,
        int questionCount) {

    public TrainingActivityAssignmentSnapshot {
        transcript = List.copyOf(transcript);
    }

    public UUID getId() {
        return assignment.getId();
    }

    public TrainingActivity getTrainingActivity() {
        return assignment.getTrainingActivity();
    }

    public GroupClassMember getGroupClassMember() {
        return assignment.getGroupClassMember();
    }

    public TrainingActivityAssignmentStatus getStatus() {
        return assignment.getStatus();
    }

    public Instant getAssignedAt() {
        return assignment.getAssignedAt();
    }

    public Instant getStartedAt() {
        return assignment.getStartedAt();
    }

    public Instant getSubmittedAt() {
        return assignment.getSubmittedAt();
    }

    public boolean isSafeBrowserLocked() {
        return assignment.isSafeBrowserLocked();
    }

    public boolean isSafeBrowserSessionActive() {
        return assignment.isSafeBrowserSessionActive();
    }
}
