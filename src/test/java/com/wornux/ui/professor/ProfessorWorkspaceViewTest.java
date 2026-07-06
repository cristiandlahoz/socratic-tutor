package com.wornux.ui.professor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.identity.Account;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.workspace.AccessibleClass;
import com.wornux.services.workspace.ProfessorWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import org.junit.jupiter.api.Test;

class ProfessorWorkspaceViewTest {

    @Test
    void beforeEnterDoesNotReenterSwitchClassForProgrammaticSelection() {
        var authenticatedUserContextUtils = mock(AuthenticatedUserContextUtils.class);
        var workspaceRoutingService = mock(WorkspaceRoutingService.class);
        var professorWorkspaceService = mock(ProfessorWorkspaceService.class);
        var beforeEnterEvent = mock(BeforeEnterEvent.class);
        var account = new Account();
        var accessibleClass = new AccessibleClass(UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Tenant",
                "MATH-101",
                "Math",
                GroupClassMemberKind.PROFESSOR);

        when(authenticatedUserContextUtils.requireCurrentAccount()).thenReturn(account);
        when(workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR)).thenReturn(true);
        when(professorWorkspaceService.listProfessorClasses(account)).thenReturn(List.of(accessibleClass));
        when(professorWorkspaceService.listStudents(account)).thenReturn(List.of());

        var view = new ProfessorWorkspaceView(authenticatedUserContextUtils,
                workspaceRoutingService,
                professorWorkspaceService);

        assertDoesNotThrow(() -> view.beforeEnter(beforeEnterEvent));

        verify(professorWorkspaceService, never()).switchClass(eq(account), any());
        verify(beforeEnterEvent, never()).forwardTo("no-access");
    }
}
