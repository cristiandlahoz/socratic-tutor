package com.wornux.data.repositories.training_activity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.training_activity.OutboxEvent;
import com.wornux.data.entities.training_activity.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            List<OutboxEventStatus> statuses, Instant availableAt, Pageable pageable);

    List<OutboxEvent> findByStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
            OutboxEventStatus status, Instant leaseUntil, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<OutboxEvent> findLockedById(UUID id);
}
