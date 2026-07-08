package com.wornux.ui.ingestion;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.Route;
import com.wornux.config.DocumentIngestionProperties;
import com.wornux.security.authorization.RequiresPermission;
import com.wornux.security.permission.AppPermission;
import com.wornux.services.document.DocumentIngestionService;
import com.wornux.services.document.DocumentWorkspaceService;
import com.wornux.services.workspace.WorkspaceDestination;
import com.wornux.ui.MainLayout;
import com.wornux.ui.components.ingestion.DocumentIngestionWorkspace;
import jakarta.annotation.security.PermitAll;

@Route(value = "documents", layout = MainLayout.class)
@PermitAll
@RequiresPermission(value = AppPermission.COURSE_MATERIAL_VIEW, workspace = WorkspaceDestination.PROFESSOR)
public class DocumentIngestionView extends Composite<Div> {

    public DocumentIngestionView(
            DocumentIngestionService ingestionService,
            DocumentWorkspaceService workspaceService,
            DocumentIngestionProperties properties) {
        var root = getContent();
        root.setId("document-ingestion-view");
        root.add(new DocumentIngestionWorkspace(ingestionService, workspaceService, properties));
    }

}
