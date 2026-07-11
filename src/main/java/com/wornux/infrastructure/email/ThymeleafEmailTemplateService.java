package com.wornux.infrastructure.email;

import com.wornux.services.email.EmailSendException;
import com.wornux.services.email.TemplatedEmailMessage;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class ThymeleafEmailTemplateService {

    private final TemplateEngine templateEngine;

    public ThymeleafEmailTemplateService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String render(TemplatedEmailMessage message) {
        try {
            var context = new Context();
            context.setVariables(message.model());
            return templateEngine.process("email/%s".formatted(message.templateName()), context);
        }
        catch (RuntimeException exception) {
            throw new EmailSendException("Failed to render email template %s".formatted(message.templateName()),
                    exception);
        }
    }
}
