package com.wornux.ui.auth;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.services.onboarding.InvitationService;
import com.wornux.services.onboarding.InvitationStateException;
import com.wornux.services.onboarding.OnboardingSessionContext;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;

@Route(value = "", autoLayout = false)
@PageTitle("Workspace")
public class LandingView extends Div implements BeforeEnterObserver {

    private final WorkspaceRoutingService workspaceRoutingService;
    private final InvitationService invitationService;
    private final OnboardingSessionContext onboardingSessionContext;

    public LandingView(
            WorkspaceRoutingService workspaceRoutingService,
            InvitationService invitationService,
            OnboardingSessionContext onboardingSessionContext) {
        this.workspaceRoutingService = workspaceRoutingService;
        this.invitationService = invitationService;
        this.onboardingSessionContext = onboardingSessionContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (onboardingSessionContext.hasActiveInvitation()) {
            try {
                var onboardingDecision = invitationService.completePendingInvitationForCurrentAccount();
                event.forwardTo(onboardingDecision.route());
                return;
            }
            catch (IllegalStateException ignored) {
                event.forwardTo(LoginView.class);
                return;
            }
            catch (InvitationStateException exception) {
                event.forwardTo(NoAccessView.class);
                return;
            }
        }

        var decision = workspaceRoutingService.resolveCurrentUserDestination();
        if (decision.destination() == WorkspaceDestination.NO_ACCESS) {
            event.forwardTo(NoAccessView.class);
            return;
        }
        event.forwardTo(decision.route());
    }
}
