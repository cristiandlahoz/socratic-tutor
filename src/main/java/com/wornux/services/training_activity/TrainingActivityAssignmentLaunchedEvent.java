package com.wornux.services.training_activity;

import java.util.Set;
import java.util.UUID;

public record TrainingActivityAssignmentLaunchedEvent(
        UUID trainingActivityId,
        UUID groupClassId,
        Set<UUID> groupClassMemberIds) {

    public TrainingActivityAssignmentLaunchedEvent {
        groupClassMemberIds = Set.copyOf(groupClassMemberIds);
    }

    public boolean affectsGroupClassMember(UUID groupClassMemberId) {
        return groupClassMemberId != null && groupClassMemberIds.contains(groupClassMemberId);
    }
}
