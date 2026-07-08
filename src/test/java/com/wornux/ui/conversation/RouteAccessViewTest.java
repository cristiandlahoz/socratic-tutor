package com.wornux.ui.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.Executor;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.RouteParameters;
import com.wornux.config.ApplicationProperties;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.identity.Account;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.chat.ModelAvailabilityService;
import com.wornux.services.crunner.CExamplePreparationService;
import com.wornux.services.crunner.CProgramDebugService;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.ingestion.DocumentIngestionView;
import com.wornux.ui.training_activity.TrainingActivityView;
import org.junit.jupiter.api.Test;

class RouteAccessViewTest {

    @Test
    void chatAllowsStudentAccess() {
        var authenticatedUserContextUtils = mock(AuthenticatedUserContextUtils.class);
        var workspaceRoutingService = mock(WorkspaceRoutingService.class);
        var viewModel = mock(ConversationViewModel.class);
        var event = mock(BeforeEnterEvent.class);
        var account = mock(Account.class);
        when(authenticatedUserContextUtils.requireCurrentAccount()).thenReturn(account);
        when(workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.PROFESSOR)).thenReturn(false);
        when(workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.STUDENT)).thenReturn(true);
        when(workspaceRoutingService.currentClassMembership(account, null))
                .thenReturn(Optional.of(mock(GroupClassMember.class)));
        when(event.getRouteParameters()).thenReturn(RouteParameters.empty());
        when(viewModel.initializeFromRoute(null, false))
                .thenReturn(ConversationViewModel.RouteInitialization.noReroute());

        var view = new ConversationView(new ConversationState(),
                viewModel,
                chatProperties(),
                mock(CProgramDebugService.class),
                mock(CExamplePreparationService.class),
                mock(Executor.class),
                authenticatedUserContextUtils,
                workspaceRoutingService,
                mock(ModelAvailabilityService.class));

        assertDoesNotThrow(() -> view.beforeEnter(event));

        verify(event, never()).forwardTo("no-access");
    }

    @Test
    void chatAllowsProfessorAccess() {
        var authenticatedUserContextUtils = mock(AuthenticatedUserContextUtils.class);
        var workspaceRoutingService = mock(WorkspaceRoutingService.class);
        var viewModel = mock(ConversationViewModel.class);
        var event = mock(BeforeEnterEvent.class);
        var account = mock(Account.class);
        when(authenticatedUserContextUtils.requireCurrentAccount()).thenReturn(account);
        when(workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.PROFESSOR)).thenReturn(true);
        when(workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.STUDENT)).thenReturn(false);
        when(workspaceRoutingService.currentClassMembership(account, null))
                .thenReturn(Optional.of(mock(GroupClassMember.class)));
        when(event.getRouteParameters()).thenReturn(RouteParameters.empty());
        when(viewModel.initializeFromRoute(null, false))
                .thenReturn(ConversationViewModel.RouteInitialization.noReroute());

        var view = new ConversationView(new ConversationState(),
                viewModel,
                chatProperties(),
                mock(CProgramDebugService.class),
                mock(CExamplePreparationService.class),
                mock(Executor.class),
                authenticatedUserContextUtils,
                workspaceRoutingService,
                mock(ModelAvailabilityService.class));

        assertDoesNotThrow(() -> view.beforeEnter(event));

        verify(event, never()).forwardTo("no-access");
    }

    @Test
    void documentsRequireProfessorWorkspaceAccess() {
        var permission = DocumentIngestionView.class.getAnnotation(RequiresPermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo(AppPermission.COURSE_MATERIAL_VIEW);
        assertThat(permission.workspace()).isEqualTo(WorkspaceDestination.PROFESSOR);
    }

    @Test
    void trainingActivitiesRequireProfessorWorkspaceAccess() {
        var permission = TrainingActivityView.class.getAnnotation(RequiresPermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo(AppPermission.TRAINING_ACTIVITY_CREATE);
        assertThat(permission.workspace()).isEqualTo(WorkspaceDestination.PROFESSOR);
    }

    private ApplicationProperties.Ai.Conversation chatProperties() {
        var properties = new ApplicationProperties.Ai.Conversation();
        properties.setContextWindowTokens(8192);
        return properties;
    }
}
