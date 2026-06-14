package com.wornux.services.subject;

import java.util.Map;

public record SubjectConfig(Long subjectId, String slug, String displayName, long version, Long revisionId,
        Map<String, Object> config, Map<String, Object> rubricDefaults, Map<String, Object> questionPolicy) {}
