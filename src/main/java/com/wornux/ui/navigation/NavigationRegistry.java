package com.wornux.ui.navigation;

import java.util.List;

import com.vaadin.flow.component.icon.SvgIcon;
import com.wornux.data.entities.authorization.ScopeLevel;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.ui.admin.SystemAdminWorkspaceView;
import com.wornux.ui.conversation.ConversationView;
import com.wornux.ui.ingestion.DocumentIngestionView;
import com.wornux.ui.professor.ProfessorWorkspaceView;
import com.wornux.ui.rbac.RoleMatrixView;
import com.wornux.ui.student.StudentWorkspaceView;
import com.wornux.ui.tenant.TenantAdminWorkspaceView;
import com.wornux.ui.training_activity.TrainingActivityView;
import org.springframework.stereotype.Component;

@Component
public class NavigationRegistry {
    private final List<NavigationEntry> entries = List.of(
            new NavigationEntry("Administración", SystemAdminWorkspaceView.class, () -> new SvgIcon("/icons/panels.svg"), ScopeLevel.PLATFORM, AppPermission.TENANT_VIEW, 10),
            new NavigationEntry("Panel profesoral", ProfessorWorkspaceView.class, () -> new SvgIcon("/icons/panels.svg"), ScopeLevel.GROUP_CLASS, AppPermission.GROUP_CLASS_MEMBER_VIEW, WorkspaceDestination.PROFESSOR, 11),
            new NavigationEntry("Panel estudiantil", StudentWorkspaceView.class, () -> new SvgIcon("/icons/panels.svg"), ScopeLevel.GROUP_CLASS, AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW, WorkspaceDestination.STUDENT, 12),
            new NavigationEntry("Institución", TenantAdminWorkspaceView.class, () -> new SvgIcon("/icons/panels.svg"), ScopeLevel.TENANT, AppPermission.GROUP_CLASS_CREATE, 20),
            new NavigationEntry("Roles y permisos", RoleMatrixView.class, () -> new SvgIcon("/icons/role.svg"), ScopeLevel.PLATFORM, AppPermission.ROLE_VIEW, 22),
            new NavigationEntry("Nueva Conversación", ConversationView.class, () -> new SvgIcon("/icons/pencil.svg"), ScopeLevel.GROUP_CLASS, AppPermission.CONVERSATION_VIEW, 30),
            new NavigationEntry("Documentos", DocumentIngestionView.class, () -> new SvgIcon("/icons/documents.svg"), ScopeLevel.GROUP_CLASS, AppPermission.COURSE_MATERIAL_VIEW, 40),
            new NavigationEntry("Actividades", TrainingActivityView.class, () -> new SvgIcon("/icons/training-activities.svg"), ScopeLevel.GROUP_CLASS, AppPermission.TRAINING_ACTIVITY_CREATE, 50));

    public List<NavigationEntry> entries() {
        return entries;
    }
}
