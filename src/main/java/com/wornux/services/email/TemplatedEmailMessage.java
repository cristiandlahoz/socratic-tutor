package com.wornux.services.email;

import java.util.Map;

public record TemplatedEmailMessage(String toAddress, String subject, String templateName, Map<String, Object> model) {}
