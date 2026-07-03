package com.wornux.ui.student;

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
import com.wornux.services.security.AuthenticatedAccountService;
import com.wornux.services.training_activity.TrainingActivityLaunchBus;
import com.wornux.services.workspace.AccessibleClass;
import com.wornux.services.workspace.StudentWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import org.junit.jupiter.api.Test;

class StudentWorkspaceViewTest {

    @Test
    void beforeEnterDoesNotReenterSwitchClassForProgrammaticSelection() {
        var authenticatedAccountService = mock(AuthenticatedAccountService.class);
        var workspaceRoutingService = mock(WorkspaceRoutingService.class);
        var studentWorkspaceService = mock(StudentWorkspaceService.class);
        var trainingActivityLaunchBus = mock(TrainingActivityLaunchBus.class);
        var beforeEnterEvent = mock(BeforeEnterEvent.class);
        var account = new Account();
        var accessibleClass = new AccessibleClass(UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Tenant",
                "MATH-101",
                "Math",
                GroupClassMemberKind.STUDENT);

        when(authenticatedAccountService.requireCurrentAccount()).thenReturn(account);
        when(workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.STUDENT)).thenReturn(true);
        when(studentWorkspaceService.listStudentClasses(account)).thenReturn(List.of(accessibleClass));
        when(studentWorkspaceService.listAssignments(account)).thenReturn(List.of());

        var view =
                new StudentWorkspaceView(
                    authenticatedAccountService,
                    workspaceRoutingService,
                    studentWorkspaceService,
                    trainingActivityLaunchBus);

        assertDoesNotThrow(() -> view.beforeEnter(beforeEnterEvent));

        verify(studentWorkspaceService, never()).switchClass(eq(account), any());
        verify(beforeEnterEvent, never()).forwardTo("no-access");
    }
}
