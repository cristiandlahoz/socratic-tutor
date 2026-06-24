package com.wornux.usecases.uc003;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.wornux.infrastructure.email.ThymeleafEmailTemplateService;
import com.wornux.services.email.TemplatedEmailMessage;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class UC003ConfigAndTemplateTest {

    @Test
    void br10_br35_mailpitExistsInComposeConfiguration() throws Exception {
        var compose = Files.readString(Path.of("compose.yml"));

        assertTrue(compose.contains("mailpit:"));
        assertTrue(compose.contains("${MAILPIT_UI_PORT:-8025}:8025"));
        assertTrue(compose.contains("${MAILPIT_SMTP_PORT:-1025}:1025"));
    }

    @Test
    void br36_devSmtpDefaultsPointToLocalhostMailpit() throws Exception {
        var applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(applicationYaml.contains("socratic:"));
        assertTrue(applicationYaml.contains("host: ${SOCRATIC_EMAIL_SMTP_HOST:localhost}"));
        assertTrue(applicationYaml.contains("port: ${SOCRATIC_EMAIL_SMTP_PORT:1025}"));
    }

    @Test
    void br39_br40_emailTemplatesRenderInvitationHtml() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");

        var templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        var service = new ThymeleafEmailTemplateService(templateEngine);
        var html = service.render(
            new TemplatedEmailMessage("professor@test.local",
                    "Professor invitation",
                    "professor-invitation",
                    Map.of(
                        "tenantName",
                        "Algorithms University",
                        "groupClassName",
                        "Algorithms 101",
                        "invitedEmail",
                        "professor@test.local",
                        "acceptUrl",
                        "http://localhost:8080/invitations/accept?token=test-token")));

        assertTrue(html.contains("You have been invited as a professor"));
        assertTrue(html.contains("Algorithms University"));
        assertTrue(html.contains("Algorithms 101"));
        assertTrue(html.contains("accept?token=test-token"));
    }
}
