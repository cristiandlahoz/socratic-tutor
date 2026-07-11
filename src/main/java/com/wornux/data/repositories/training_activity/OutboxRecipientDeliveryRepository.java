package com.wornux.data.repositories.training_activity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wornux.data.entities.training_activity.OutboxRecipientDelivery;
import com.wornux.data.entities.training_activity.OutboxRecipientDeliveryStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

public interface OutboxRecipientDeliveryRepository extends JpaRepository<OutboxRecipientDelivery, UUID> {

    @EntityGraph(attributePaths = {"outboxEvent", "groupClassMember", "groupClassMember.tenantAccount", "groupClassMember.tenantAccount.account"})
    List<OutboxRecipientDelivery> findByOutboxEvent_IdAndStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            UUID outboxEventId, List<OutboxRecipientDeliveryStatus> statuses, Instant availableAt);

    @EntityGraph(attributePaths = {"outboxEvent", "groupClassMember", "groupClassMember.tenantAccount", "groupClassMember.tenantAccount.account"})
    List<OutboxRecipientDelivery> findByOutboxEvent_IdAndStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            UUID outboxEventId, List<OutboxRecipientDeliveryStatus> statuses, Instant availableAt, Pageable pageable);

    long countByOutboxEvent_IdAndStatusNot(UUID outboxEventId, OutboxRecipientDeliveryStatus status);

    long countByOutboxEvent_IdAndStatus(UUID outboxEventId, OutboxRecipientDeliveryStatus status);

    long countByStatus(OutboxRecipientDeliveryStatus status);

    long countByStatusAndAttemptCountGreaterThan(OutboxRecipientDeliveryStatus status, int attemptCount);

    List<OutboxRecipientDelivery> findByOutboxEvent_IdAndStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
            UUID outboxEventId, OutboxRecipientDeliveryStatus status, Instant leaseUntil, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"outboxEvent", "groupClassMember", "groupClassMember.tenantAccount", "groupClassMember.tenantAccount.account"})
    java.util.Optional<OutboxRecipientDelivery> findLockedById(UUID id);
}
