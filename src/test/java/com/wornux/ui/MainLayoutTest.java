package com.wornux.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MainLayoutTest {

    @Test
    void professorSidebarShowsChatDocumentsAndEvaluations() {
        var access = MainLayout.buildSidebarNavigationAccess(true, false);

        assertTrue(access.showChat());
        assertTrue(access.showDocuments());
        assertTrue(access.showEvaluations());
    }

    @Test
    void studentSidebarHidesProfessorOnlyNavigation() {
        var access = MainLayout.buildSidebarNavigationAccess(false, true);

        assertTrue(access.showChat());
        assertFalse(access.showDocuments());
        assertFalse(access.showEvaluations());
    }
}
