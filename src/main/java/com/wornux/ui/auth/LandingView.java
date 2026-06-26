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
import com.wornux.ui.admin.SystemAdminWorkspaceView;
import com.wornux.ui.professor.ProfessorWorkspaceView;
import com.wornux.ui.student.StudentWorkspaceView;
import com.wornux.ui.tenant.TenantAdminWorkspaceView;
import jakarta.annotation.security.PermitAll;

@Route(value = "", autoLayout = false)
@PageTitle("Workspace")
@PermitAll
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
                forwardToDestination(event, onboardingDecision.destination());
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
        forwardToDestination(event, decision.destination());
    }

    private void forwardToDestination(BeforeEnterEvent event, WorkspaceDestination destination) {
        switch (destination) {
            case SYSTEM_ADMIN -> event.forwardTo(SystemAdminWorkspaceView.class);
            case TENANT_ADMIN -> event.forwardTo(TenantAdminWorkspaceView.class);
            case PROFESSOR -> event.forwardTo(ProfessorWorkspaceView.class);
            case STUDENT -> event.forwardTo(StudentWorkspaceView.class);
            case NO_ACCESS -> event.forwardTo(NoAccessView.class);
        }
    }
}
