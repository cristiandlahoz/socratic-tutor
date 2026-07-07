package com.wornux.ui.ingestion;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.wornux.config.DocumentIngestionProperties;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.document.DocumentIngestionService;
import com.wornux.services.document.DocumentWorkspaceService;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.services.workspace.WorkspaceRoutingService;
import com.wornux.ui.MainLayout;
import com.wornux.ui.auth.NoAccessView;
import com.wornux.ui.components.ingestion.DocumentIngestionWorkspace;
import jakarta.annotation.security.PermitAll;

@Route(value = "documents", layout = MainLayout.class)
@PermitAll
@RequiresPermission(AppPermission.COURSE_MATERIAL_VIEW)
public class DocumentIngestionView extends Composite<Div> implements BeforeEnterObserver {

    private final transient AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final transient WorkspaceRoutingService workspaceRoutingService;

    public DocumentIngestionView(
            DocumentIngestionService ingestionService,
            DocumentWorkspaceService workspaceService,
            DocumentIngestionProperties properties,
            AuthenticatedUserContextUtils authenticatedUserContextUtils,
            WorkspaceRoutingService workspaceRoutingService) {
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
        this.workspaceRoutingService = workspaceRoutingService;

        var root = getContent();
        root.setId("document-ingestion-view");
        root.add(new DocumentIngestionWorkspace(ingestionService, workspaceService, properties));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var account = authenticatedUserContextUtils.requireCurrentAccount();
        if (!workspaceRoutingService.prepareWorkspaceAccess(account, WorkspaceDestination.PROFESSOR)) {
            event.forwardTo(NoAccessView.class);
        }
    }
}
