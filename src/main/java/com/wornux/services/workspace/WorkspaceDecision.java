package com.wornux.services.workspace;

import java.util.UUID;

public record WorkspaceDecision(WorkspaceDestination destination, UUID tenantAccountId, UUID groupClassMemberId) {

    public String route() {
        return destination.route();
    }
}
