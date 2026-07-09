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

import com.vaadin.flow.router.AfterNavigationEvent;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.identity.Account;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.training_activity.TrainingActivityLaunchedBus;
import com.wornux.services.workspace.AccessibleClass;
import com.wornux.services.workspace.StudentWorkspaceService;
import org.junit.jupiter.api.Test;

class StudentWorkspaceViewTest {

    @Test
    void afterNavigationDoesNotReenterSwitchClassForProgrammaticSelection() {
        var authenticatedUserContextUtils = mock(AuthenticatedUserContextUtils.class);
        var studentWorkspaceService = mock(StudentWorkspaceService.class);
        var activityLaunchedBus = mock(TrainingActivityLaunchedBus.class);
        var afterNavigationEvent = mock(AfterNavigationEvent.class);
        var account = new Account();
        var accessibleClass = new AccessibleClass(UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Tenant",
                "MATH-101",
                "Math",
                GroupClassMemberKind.STUDENT);

        when(authenticatedUserContextUtils.requireCurrentAccount()).thenReturn(account);
        when(studentWorkspaceService.listStudentClasses(account)).thenReturn(List.of(accessibleClass));
        when(studentWorkspaceService.listAssignments(account)).thenReturn(List.of());

        var view = new StudentWorkspaceView(authenticatedUserContextUtils, studentWorkspaceService, activityLaunchedBus);

        assertDoesNotThrow(() -> view.afterNavigation(afterNavigationEvent));

        verify(studentWorkspaceService, never()).switchClass(eq(account), any());
    }
}
