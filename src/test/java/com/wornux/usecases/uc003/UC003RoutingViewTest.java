package com.wornux.usecases.uc003;

import static org.mockito.Mockito.*;

import java.util.UUID;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.wornux.services.onboarding.InvitationService;
import com.wornux.services.onboarding.OnboardingSessionContext;
import com.wornux.services.workspace.WorkspaceDecision;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.auth.LandingView;
import com.wornux.ui.auth.LoginView;
import com.wornux.ui.auth.NoAccessView;
import org.junit.jupiter.api.Test;

class UC003RoutingViewTest {

    @Test
    void br01_landingForwardsToLoginWhenOnboardingNeedsAuthentication() {
        var routingService = mock(WorkspaceRoutingService.class);
        var invitationService = mock(InvitationService.class);
        var onboardingContext = new OnboardingSessionContext();
        onboardingContext.setInvitationId(1L);
        var view = new LandingView(routingService, invitationService, onboardingContext);
        var event = mock(BeforeEnterEvent.class);
        when(invitationService.completePendingInvitationForCurrentAccount())
                .thenThrow(new IllegalStateException("login required"));

        view.beforeEnter(event);

        verify(event).forwardTo(LoginView.class);
    }

    @Test
    void br02_br03_br04_br05_br06_landingUsesRoleBasedWorkspaceDestination() {
        var routingService = mock(WorkspaceRoutingService.class);
        var invitationService = mock(InvitationService.class);
        var onboardingContext = new OnboardingSessionContext();
        var view = new LandingView(routingService, invitationService, onboardingContext);
        var event = mock(BeforeEnterEvent.class);
        when(routingService.resolveCurrentUserDestination()).thenReturn(
            new WorkspaceDecision(WorkspaceDestination.PROFESSOR, UUID.randomUUID(), UUID.randomUUID()));

        view.beforeEnter(event);

        verify(event).forwardTo("professor");
    }

    @Test
    void br54_landingForwardsToNoAccessWhenNoWorkspaceExists() {
        var routingService = mock(WorkspaceRoutingService.class);
        var invitationService = mock(InvitationService.class);
        var onboardingContext = new OnboardingSessionContext();
        var view = new LandingView(routingService, invitationService, onboardingContext);
        var event = mock(BeforeEnterEvent.class);
        when(routingService.resolveCurrentUserDestination())
                .thenReturn(new WorkspaceDecision(WorkspaceDestination.NO_ACCESS, null, null));

        view.beforeEnter(event);

        verify(event).forwardTo(NoAccessView.class);
    }
}
