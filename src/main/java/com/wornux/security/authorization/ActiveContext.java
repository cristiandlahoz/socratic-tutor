package com.wornux.security.authorization;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import com.wornux.data.entities.identity.ContextLevel;
import org.jspecify.annotations.Nullable;

public record ActiveContext(ContextLevel level, @Nullable UUID tenantId, @Nullable UUID groupClassId) implements Serializable {

    public ActiveContext {
        Objects.requireNonNull(level, "level must not be null");
        if (level == ContextLevel.PLATFORM) {
            tenantId = null;
            groupClassId = null;
        }
        if (level == ContextLevel.TENANT) {
            Objects.requireNonNull(tenantId, "tenantId must not be null for tenant context");
            groupClassId = null;
        }
        if (level == ContextLevel.GROUP_CLASS) {
            Objects.requireNonNull(tenantId, "tenantId must not be null for group-class context");
            Objects.requireNonNull(groupClassId, "groupClassId must not be null for group-class context");
        }
    }

    public static ActiveContext platform() {
        return new ActiveContext(ContextLevel.PLATFORM, null, null);
    }

    public static ActiveContext tenant(UUID tenantId) {
        return new ActiveContext(ContextLevel.TENANT, tenantId, null);
    }

    public static ActiveContext groupClass(UUID tenantId, UUID groupClassId) {
        return new ActiveContext(ContextLevel.GROUP_CLASS, tenantId, groupClassId);
    }
}
