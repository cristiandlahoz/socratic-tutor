package com.wornux.services.onboarding;

import java.util.HashMap;

import com.wornux.config.SocraticEmailProperties;
import com.wornux.data.entities.onboarding.Invitation;
import com.wornux.services.email.EmailMessage;
import com.wornux.services.email.EmailService;
import com.wornux.services.email.EmailTemplateService;
import com.wornux.services.email.TemplatedEmailMessage;
import org.springframework.stereotype.Service;

@Service
public class InvitationEmailService {

    private final SocraticEmailProperties emailProperties;
    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;

    public InvitationEmailService(
            SocraticEmailProperties emailProperties,
            EmailTemplateService emailTemplateService,
            EmailService emailService) {
        this.emailProperties = emailProperties;
        this.emailTemplateService = emailTemplateService;
        this.emailService = emailService;
    }

    public void sendInvitation(Invitation invitation, String rawToken) {
        var model = new HashMap<String, Object>();
        model.put("tenantName", invitation.getTenant().getName());
        model.put("groupClassName", invitation.getGroupClass() == null ? null : invitation.getGroupClass().getName());
        model.put("invitedEmail", invitation.getInvitedEmail());
        model.put("targetRole", invitation.getTargetRole().name());
        model.put("acceptUrl", emailProperties.getInvitationBaseUrl() + "/invitations/accept?token=" + rawToken);

        var templateName = switch (invitation.getTargetRole()) {
            case TENANT_ADMIN -> "tenant-admin-invitation";
            case PROFESSOR -> "professor-invitation";
            case STUDENT -> "student-invitation";
        };
        var subject = switch (invitation.getTargetRole()) {
            case TENANT_ADMIN -> "You have been invited as a tenant admin";
            case PROFESSOR -> "You have been invited as a professor";
            case STUDENT -> "You have been invited as a student";
        };
        var html = emailTemplateService
                .render(new TemplatedEmailMessage(invitation.getInvitedEmail(), subject, templateName, model));
        emailService.send(new EmailMessage(invitation.getInvitedEmail(), subject, html));
    }
}
