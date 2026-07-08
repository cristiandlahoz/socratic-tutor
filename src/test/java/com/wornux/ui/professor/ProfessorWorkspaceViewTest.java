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

import com.vaadin.flow.router.AfterNavigationEvent;
import com.wornux.data.entities.academic.GroupClassMemberKind;
import com.wornux.data.entities.identity.Account;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.workspace.AccessibleClass;
import com.wornux.services.workspace.ProfessorWorkspaceService;
import org.junit.jupiter.api.Test;

class ProfessorWorkspaceViewTest {

    @Test
    void afterNavigationDoesNotReenterSwitchClassForProgrammaticSelection() {
        var authenticatedUserContextUtils = mock(AuthenticatedUserContextUtils.class);
        var professorWorkspaceService = mock(ProfessorWorkspaceService.class);
        var afterNavigationEvent = mock(AfterNavigationEvent.class);
        var account = new Account();
        var accessibleClass = new AccessibleClass(UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Tenant",
                "MATH-101",
                "Math",
                GroupClassMemberKind.PROFESSOR);

        when(authenticatedUserContextUtils.requireCurrentAccount()).thenReturn(account);
        when(professorWorkspaceService.listProfessorClasses(account)).thenReturn(List.of(accessibleClass));
        when(professorWorkspaceService.listStudents(account)).thenReturn(List.of());

        var view = new ProfessorWorkspaceView(authenticatedUserContextUtils, professorWorkspaceService);

        assertDoesNotThrow(() -> view.afterNavigation(afterNavigationEvent));

        verify(professorWorkspaceService, never()).switchClass(eq(account), any());
    }
}
