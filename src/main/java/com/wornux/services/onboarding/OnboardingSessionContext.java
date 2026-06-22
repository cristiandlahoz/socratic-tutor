package com.wornux.services.onboarding;

import com.wornux.data.entities.onboarding.InvitationTargetRole;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

@Component
@SessionScope
@Getter
@Setter
public class OnboardingSessionContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private UUID invitationId;
    private String invitedEmail;
    private InvitationTargetRole targetRole;
    private UUID tenantId;
    private UUID groupClassId;
    private String postAcceptRedirect;
    private Instant validatedAt;
    private boolean accountAlreadyExists;

    public boolean hasActiveInvitation() {
        return invitationId != null;
    }

    public void clear() {
        invitationId = null;
        invitedEmail = null;
        targetRole = null;
        tenantId = null;
        groupClassId = null;
        postAcceptRedirect = null;
        validatedAt = null;
        accountAlreadyExists = false;
    }
}
