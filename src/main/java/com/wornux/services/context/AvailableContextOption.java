package com.wornux.services.context;

import java.util.Objects;
import java.util.UUID;

import com.wornux.data.entities.identity.ContextLevel;
import com.wornux.security.authorization.ActiveContext;

public record AvailableContextOption(ContextLevel level, UUID tenantId, UUID classId, String label, String subtitle,
        String identityLabel) {

    public ActiveContext toActiveContext() {
        return switch (level) {
            case PLATFORM -> ActiveContext.platform();
            case TENANT -> ActiveContext.tenant(tenantId);
            case GROUP_CLASS -> ActiveContext.groupClass(tenantId, classId);
        };
    }

    public boolean matches(ActiveContext context) {
        return level == context.level()
                && Objects.equals(tenantId, context.tenantId())
                && Objects.equals(classId, context.groupClassId());
    }
}
