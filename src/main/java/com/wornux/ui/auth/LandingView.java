package com.wornux.ui.auth;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.services.onboarding.InvitationService;
import com.wornux.services.onboarding.InvitationStateException;
import com.wornux.services.onboarding.OnboardingSessionContext;
import com.wornux.services.context.ContextSelectionResult;
import com.wornux.services.context.ContextSelectionService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.ui.admin.SystemAdminWorkspaceView;
import com.wornux.ui.professor.ProfessorWorkspaceView;
import com.wornux.ui.student.StudentWorkspaceView;
import com.wornux.ui.tenant.TenantAdminWorkspaceView;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import jakarta.annotation.security.PermitAll;

@Route(value = "", autoLayout = false)
@PageTitle("Workspace")
@PermitAll
public class LandingView extends Div implements BeforeEnterObserver {

    private final transient InvitationService invitationService;
    private final transient OnboardingSessionContext onboardingSessionContext;
    private final transient AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final transient ContextSelectionService contextSelectionService;

    public LandingView(
            InvitationService invitationService,
            OnboardingSessionContext onboardingSessionContext,
            AuthenticatedUserContextUtils authenticatedUserContextUtils,
            ContextSelectionService contextSelectionService) {
        this.invitationService = invitationService;
        this.onboardingSessionContext = onboardingSessionContext;
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
        this.contextSelectionService = contextSelectionService;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (onboardingSessionContext.hasActiveInvitation()) {
            try {
                var onboardingDecision = invitationService.completePendingInvitationForCurrentAccount();
                forwardToDestination(event, onboardingDecision.destination());
                return;
            }
            catch (IllegalStateException _) {
                event.forwardTo(LoginView.class);
                return;
            }
            catch (InvitationStateException _) {
                event.forwardTo(NoAccessView.class);
                return;
            }
        }

        switch (contextSelectionService.resolveLoginContext(authenticatedUserContextUtils.requireCurrentAccount())) {
            case ContextSelectionResult.NoAccess _ -> event.forwardTo(NoAccessView.class);
            case ContextSelectionResult.SelectionRequired _ -> event.forwardTo(ContextSelectionView.class);
            case ContextSelectionResult.Selected (var option) -> event.forwardTo(contextSelectionService.defaultRoute(option));
        }
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
