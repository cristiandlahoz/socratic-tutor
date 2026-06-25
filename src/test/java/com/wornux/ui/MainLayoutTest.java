package com.wornux.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wornux.ui.layout.MainLayoutAccess;
import org.junit.jupiter.api.Test;

class MainLayoutTest {

    @Test
    void professorSidebarShowsChatDocumentsAndEvaluations() {
        var access = new MainLayoutAccess(false, false, true, false);

        assertTrue(access.canChat());
        assertTrue(access.canManageDocuments());
        assertTrue(access.canManageActivities());
    }

    @Test
    void studentSidebarHidesProfessorOnlyNavigation() {
        var access = new MainLayoutAccess(false, false, false, true);

        assertTrue(access.canChat());
        assertFalse(access.canManageDocuments());
        assertFalse(access.canManageActivities());
    }

    @Test
    void adminSidebarHidesChatNavigation() {
        var access = new MainLayoutAccess(true, false, false, false);

        assertFalse(access.canChat());
        assertFalse(access.canManageDocuments());
        assertFalse(access.canManageActivities());
    }
}
