package com.wornux.services.onboarding;

import com.wornux.data.entities.onboarding.InvitationTargetRole;
import java.util.UUID;

public record OnboardingStart(
        UUID invitationId,
        String invitedEmail,
        InvitationTargetRole targetRole,
        boolean accountAlreadyExists) {}
