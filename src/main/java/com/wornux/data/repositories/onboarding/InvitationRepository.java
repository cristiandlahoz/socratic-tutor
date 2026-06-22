package com.wornux.data.repositories.onboarding;

import com.wornux.data.entities.onboarding.Invitation;
import com.wornux.data.entities.onboarding.InvitationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findByTokenHash(String tokenHash);

    List<Invitation> findByTenant_IdOrderByCreatedAtDesc(UUID tenantId);

    List<Invitation> findByGroupClass_IdOrderByCreatedAtDesc(UUID groupClassId);

    boolean existsByInvitedEmailIgnoreCaseAndStatusIn(String invitedEmail, List<InvitationStatus> statuses);
}
