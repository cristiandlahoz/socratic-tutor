package com.wornux.services.subject;

import java.util.Map;
import java.util.UUID;

public record SubjectConfig(UUID subjectId, String slug, String displayName, long version, UUID revisionId,
        Map<String, Object> config, Map<String, Object> rubricDefaults, Map<String, Object> questionPolicy) {}
