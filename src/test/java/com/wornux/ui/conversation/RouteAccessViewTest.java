package com.wornux.ui.conversation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.Executor;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.wornux.config.ChatProperties;
import com.wornux.config.DocumentIngestionProperties;
import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.identity.Account;
import com.wornux.services.chat.ModelAvailabilityService;
import com.wornux.services.crunner.CExamplePreparationService;
import com.wornux.services.crunner.CProgramDebugService;
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.training_activity.TrainingActivityService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.auth.NoAccessView;
import com.wornux.ui.ingestion.DocumentIngestionState;
import com.wornux.ui.ingestion.DocumentIngestionUiController;
import com.wornux.ui.ingestion.DocumentIngestionView;
import com.wornux.ui.training_activity.TrainingActivityView;
import org.junit.jupiter.api.Test;

class RouteAccessViewTest {

    @Test
    void chatAllowsStudentAccess() {
        var authenticatedAccountService = mock(AuthenticatedAccountService.class);
        var workspaceRoutingService = mock(WorkspaceRoutingService.class);
        var viewModel = mock(ConversationViewModel.class);
        var event = mock(BeforeEnterEvent.class);
        var account = mock(Account.class);
        when(authenticatedAccountService.requireCurrentAccount()).thenReturn(account);
        when(workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.PROFESSOR)).thenReturn(false);
        when(workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.STUDENT)).thenReturn(true);
        when(workspaceRoutingService.currentClassMembership(account, null))
                .thenReturn(Optional.of(mock(GroupClassMember.class)));
        when(event.getLocation()).thenReturn(new Location("chat"));
        when(viewModel.initializeFromRoute(null, false))
                .thenReturn(ConversationViewModel.RouteInitialization.noReroute());

        var view = new ConversationView(new ConversationState(),
                viewModel,
                new ChatProperties(),
                mock(CProgramDebugService.class),
                mock(CExamplePreparationService.class),
                mock(Executor.class),
                authenticatedAccountService,
                workspaceRoutingService,
                mock(ModelAvailabilityService.class));

        assertDoesNotThrow(() -> view.beforeEnter(event));

        verify(event, never()).forwardTo("no-access");
    }

    @Test
    void chatAllowsProfessorAccess() {
        var authenticatedAccountService = mock(AuthenticatedAccountService.class);
        var workspaceRoutingService = mock(WorkspaceRoutingService.class);
        var viewModel = mock(ConversationViewModel.class);
        var event = mock(BeforeEnterEvent.class);
        var account = mock(Account.class);
        when(authenticatedAccountService.requireCurrentAccount()).thenReturn(account);
        when(workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.PROFESSOR)).thenReturn(true);
        when(workspaceRoutingService.canAccessWorkspace(account, WorkspaceDestination.STUDENT)).thenReturn(false);
        when(workspaceRoutingService.currentClassMembership(account, null))
                .thenReturn(Optional.of(mock(GroupClassMember.class)));
        when(event.getLocation()).thenReturn(new Location("chat"));
        when(viewModel.initializeFromRoute(null, false))
                .thenReturn(ConversationViewModel.RouteInitialization.noReroute());

        var view = new ConversationView(new ConversationState(),
                viewModel,
                new ChatProperties(),
                mock(CProgramDebugService.class),
                mock(CExamplePreparationService.class),
                mock(Executor.class),
                authenticatedAccountService,
                workspaceRoutingService,
                mock(ModelAvailabilityService.class));

        assertDoesNotThrow(() -> view.beforeEnter(event));

        verify(event, never()).forwardTo("no-access");
    }

    @Test
    void documentsDenyStudentAccess() {
        var authenticatedAccountService = mock(AuthenticatedAccountService.class);
        var workspaceRoutingService = mock(WorkspaceRoutingService.class);
        var controller = mock(DocumentIngestionUiController.class);
        var event = mock(BeforeEnterEvent.class);
        var account = mock(Account.class);
        when(authenticatedAccountService.requireCurrentAccount()).thenReturn(account);
        when(workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR)).thenReturn(false);

        var view = new DocumentIngestionView(controller,
                new DocumentIngestionState(),
                new DocumentIngestionProperties(),
                authenticatedAccountService,
                workspaceRoutingService);

        view.beforeEnter(event);

        verify(event).forwardTo(NoAccessView.class);
    }

    @Test
    void trainingActivitiesDenyStudentAccess() {
        var authenticatedAccountService = mock(AuthenticatedAccountService.class);
        var workspaceRoutingService = mock(WorkspaceRoutingService.class);
        var trainingActivityService = mock(TrainingActivityService.class);
        var event = mock(BeforeEnterEvent.class);
        var account = mock(Account.class);
        when(authenticatedAccountService.requireCurrentAccount()).thenReturn(account);
        when(workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR)).thenReturn(false);

        var view =
                new TrainingActivityView(trainingActivityService, workspaceRoutingService, authenticatedAccountService);

        view.beforeEnter(event);

        verify(event).forwardTo(NoAccessView.class);
    }
}
