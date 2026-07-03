package com.wornux.security.authorization;

import java.util.UUID;

public record RbacChangedEvent(UUID roleNamespaceId) {
}
