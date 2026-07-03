package com.wornux.ui.navigation;

import java.util.List;

import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.security.permission.AppPermission;
import com.wornux.ui.admin.SystemAdminWorkspaceView;
import com.wornux.ui.conversation.ConversationView;
import com.wornux.ui.ingestion.DocumentIngestionView;
import com.wornux.ui.rbac.GroupClassMemberRoleAssignmentView;
import com.wornux.ui.rbac.RoleMatrixView;
import com.wornux.ui.rbac.TenantMemberRoleAssignmentView;
import com.wornux.ui.tenant.TenantAdminWorkspaceView;
import com.wornux.ui.training_activity.TrainingActivityView;
import org.springframework.stereotype.Component;

@Component
public class NavigationRegistry {
    private final List<NavigationEntry> entries = List.of(
            new NavigationEntry("Administración", SystemAdminWorkspaceView.class, ContextLevel.PLATFORM, AppPermission.TENANT_VIEW, 10),
            new NavigationEntry("Institución", TenantAdminWorkspaceView.class, ContextLevel.TENANT, AppPermission.GROUP_CLASS_CREATE, 20),
            new NavigationEntry("Matriz de roles", RoleMatrixView.class, ContextLevel.PLATFORM, AppPermission.ROLE_VIEW, 22),
            new NavigationEntry("Roles de tenant", TenantMemberRoleAssignmentView.class, ContextLevel.TENANT, AppPermission.ROLE_ASSIGN, 24),
            new NavigationEntry("Roles de clase", GroupClassMemberRoleAssignmentView.class, ContextLevel.TENANT, AppPermission.ROLE_ASSIGN, 26),
            new NavigationEntry("Conversación", ConversationView.class, ContextLevel.GROUP_CLASS, AppPermission.CONVERSATION_VIEW, 30),
            new NavigationEntry("Documentos", DocumentIngestionView.class, ContextLevel.GROUP_CLASS, AppPermission.COURSE_MATERIAL_VIEW, 40),
            new NavigationEntry("Actividades", TrainingActivityView.class, ContextLevel.GROUP_CLASS, AppPermission.TRAINING_ACTIVITY_VIEW, 50));

    public List<NavigationEntry> entries() {
        return entries;
    }
}
