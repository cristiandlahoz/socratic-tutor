package com.wornux.services.onboarding;

import com.wornux.data.entities.onboarding.InvitationTargetRole;

public record OnboardingStart(Long invitationId, String invitedEmail, InvitationTargetRole targetRole,
        boolean accountAlreadyExists) {}
