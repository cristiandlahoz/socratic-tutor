package com.wornux.data.repositories.training_activity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wornux.data.entities.training_activity.SafeBrowserSession;
import com.wornux.data.entities.training_activity.SafeBrowserSessionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafeBrowserSessionRepository extends JpaRepository<SafeBrowserSession, UUID> {

    @EntityGraph(attributePaths = {"assignment", "assignment.trainingActivity", "assignment.groupClassMember"})
    Optional<SafeBrowserSession> findFirstByAssignment_IdAndStatusInOrderByCreatedAtDesc(
            UUID assignmentId, Collection<SafeBrowserSessionStatus> statuses);

    @EntityGraph(attributePaths = {"assignment", "assignment.trainingActivity", "assignment.groupClassMember"})
    List<SafeBrowserSession> findByStatusAndCreatedAtBefore(SafeBrowserSessionStatus status, Instant cutoff);

    @EntityGraph(attributePaths = {"assignment", "assignment.trainingActivity", "assignment.groupClassMember"})
    List<SafeBrowserSession> findByStatusAndLastHeartbeatAtBefore(SafeBrowserSessionStatus status, Instant cutoff);
}
