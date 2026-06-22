package com.wornux.services.workspace;

import java.util.List;
import java.util.UUID;

public record AccessibleTenant(UUID tenantId, UUID tenantAccountId, String tenantName, List<String> roleCodes) {}
