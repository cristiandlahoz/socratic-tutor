package com.wornux.ui.navigation;

import java.util.List;

import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.security.permission.AppPermission;
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
            new NavigationEntry("Administración", SystemAdminWorkspaceView.class, () -> new Icon(VaadinIcon.HOME), ContextLevel.PLATFORM, AppPermission.TENANT_VIEW, 10),
            new NavigationEntry("Panel profesoral", ProfessorWorkspaceView.class, () -> new Icon(VaadinIcon.ACADEMY_CAP), ContextLevel.GROUP_CLASS, AppPermission.GROUP_CLASS_MEMBER_VIEW, 11),
            new NavigationEntry("Panel estudiantil", StudentWorkspaceView.class, () -> new Icon(VaadinIcon.USER), ContextLevel.GROUP_CLASS, AppPermission.TRAINING_ACTIVITY_ASSIGNMENT_VIEW, 12),
            new NavigationEntry("Institución", TenantAdminWorkspaceView.class, () -> new Icon(VaadinIcon.INSTITUTION), ContextLevel.TENANT, AppPermission.GROUP_CLASS_CREATE, 20),
            new NavigationEntry("Roles y permisos", RoleMatrixView.class, () -> new Icon(VaadinIcon.KEY), ContextLevel.PLATFORM, AppPermission.ROLE_VIEW, 22),
            new NavigationEntry("Conversación", ConversationView.class, () -> new SvgIcon("/icons/pencil.svg"), ContextLevel.GROUP_CLASS, AppPermission.CONVERSATION_VIEW, 30),
            new NavigationEntry("Documentos", DocumentIngestionView.class, () -> new Icon(VaadinIcon.FILE_TEXT), ContextLevel.GROUP_CLASS, AppPermission.COURSE_MATERIAL_VIEW, 40),
            new NavigationEntry("Actividades", TrainingActivityView.class, () -> new Icon(VaadinIcon.TASKS), ContextLevel.GROUP_CLASS, AppPermission.TRAINING_ACTIVITY_CREATE, 50));

    public List<NavigationEntry> entries() {
        return entries;
    }
}
