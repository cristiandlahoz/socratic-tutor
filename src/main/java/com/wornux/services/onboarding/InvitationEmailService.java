package com.wornux.services.onboarding;

import java.util.HashMap;

import com.wornux.config.ApplicationProperties;
import com.wornux.data.entities.onboarding.Invitation;
import com.wornux.services.email.EmailMessage;
import com.wornux.infrastructure.email.SmtpEmailService;
import com.wornux.infrastructure.email.ThymeleafEmailTemplateService;
import com.wornux.services.email.TemplatedEmailMessage;
import org.springframework.stereotype.Service;

@Service
public class InvitationEmailService {

    private final ApplicationProperties.Email emailProperties;
    private final ThymeleafEmailTemplateService emailTemplateService;
    private final SmtpEmailService emailService;

    public InvitationEmailService(
            ApplicationProperties.Email emailProperties,
            ThymeleafEmailTemplateService emailTemplateService,
            SmtpEmailService emailService) {
        this.emailProperties = emailProperties;
        this.emailTemplateService = emailTemplateService;
        this.emailService = emailService;
    }

    public void sendInvitation(Invitation invitation, String rawToken) {
        var model = new HashMap<String, Object>();
        var acceptUrl = "%s/invitations/accept?token=%s".formatted(emailProperties.getInvitationBaseUrl(), rawToken);
        model.put("acceptUrl", acceptUrl);

        var subject = switch (invitation.getTargetRole()) {
            case TENANT_ADMIN -> "Invitation to manage %s".formatted(invitation.getTenant().getName());
            case PROFESSOR -> "Invitation to teach %s".formatted(invitation.getGroupClass().getName());
            case STUDENT -> "Invitation to join %s".formatted(invitation.getGroupClass().getName());
        };
        var headline = switch (invitation.getTargetRole()) {
            case TENANT_ADMIN -> "Manage %s".formatted(invitation.getTenant().getName());
            case PROFESSOR -> "Teach %s".formatted(invitation.getGroupClass().getName());
            case STUDENT -> "Join %s".formatted(invitation.getGroupClass().getName());
        };
        var intro = switch (invitation.getTargetRole()) {
            case TENANT_ADMIN -> "You were invited to manage this institution in Socratic Tutor.";
            case PROFESSOR -> "You were invited to teach this class in Socratic Tutor.";
            case STUDENT -> "You were invited to join this class in Socratic Tutor.";
        };
        model.put("headline", headline);
        model.put("intro", intro);

        var html = emailTemplateService
                .render(new TemplatedEmailMessage(invitation.getInvitedEmail(), subject, "invitation", model));
        var plainText =
                "%s%n%n%s%n%nAccept invitation:%n%s%n%nIf you were not expecting this invitation, you can ignore this email."
                        .formatted(headline, intro, acceptUrl);
        emailService.send(new EmailMessage(invitation.getInvitedEmail(), subject, plainText, html));
    }
}
