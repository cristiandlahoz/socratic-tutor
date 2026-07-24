package com.wornux.services.email;

public interface EmailTemplateService {
    String render(TemplatedEmailMessage message);
}
