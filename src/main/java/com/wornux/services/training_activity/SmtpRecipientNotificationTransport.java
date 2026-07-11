package com.wornux.services.training_activity;

import java.util.List;

import com.wornux.config.ApplicationProperties;
import com.wornux.services.email.EmailMessage;
import com.wornux.services.email.EmailService;
import com.wornux.services.email.EmailTemplateService;
import com.wornux.services.email.TemplatedEmailMessage;
import org.springframework.stereotype.Service;

/**
 * SMTP acknowledges a message but cannot prove that a connection failure happened before acceptance.
 * Such a result is deliberately uncertain and must be manually replayed instead of retried automatically.
 */
@Service
public class SmtpRecipientNotificationTransport implements RecipientNotificationTransport {

    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final ApplicationProperties.Email emailProperties;

    public SmtpRecipientNotificationTransport(
            EmailService emailService,
            EmailTemplateService emailTemplateService,
            ApplicationProperties.Email emailProperties) {
        this.emailService = emailService;
        this.emailTemplateService = emailTemplateService;
        this.emailProperties = emailProperties;
    }

    @Override
    public DeliveryOutcome deliver(ActivityPublicationNotificationService.DeliveryMessage message) {
        var studentHomeUrl = "%s/student".formatted(emailProperties.getInvitationBaseUrl());
        var subject = "New formative activity";
        var model = java.util.Map.<String, Object>of(
                "headline", "A new formative activity is available",
                "intro", "Open your student workspace to review the assigned activity.",
                "activityUrl", studentHomeUrl);
        var html = emailTemplateService.render(new TemplatedEmailMessage(
                message.emailAddress(), subject, "training-activity-invitation", model));
        try {
            emailService.send(new EmailMessage(message.emailAddress(), subject,
                    "A new formative activity is available: %s".formatted(studentHomeUrl), html, List.of(), message.idempotencyKey()));
            return DeliveryOutcome.ACCEPTED;
        }
        catch (RuntimeException exception) {
            return DeliveryOutcome.UNCERTAIN_AFTER_SEND;
        }
    }
}
