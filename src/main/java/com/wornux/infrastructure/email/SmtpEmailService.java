package com.wornux.infrastructure.email;

import com.wornux.config.ApplicationProperties;
import com.wornux.services.email.EmailMessage;
import com.wornux.services.email.EmailSendException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService {

    private final JavaMailSender mailSender;
    private final ApplicationProperties.Email emailProperties;

    public SmtpEmailService(JavaMailSender mailSender, ApplicationProperties.Email emailProperties) {
        this.mailSender = mailSender;
        this.emailProperties = emailProperties;
    }

    public void send(EmailMessage message) {
        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setTo(message.toAddress());
            helper.setSubject(message.subject());
            if (message.plainTextBody() == null || message.plainTextBody().isBlank()) {
                helper.setText(message.htmlBody(), true);
            }
            else {
                helper.setText(message.plainTextBody(), message.htmlBody());
            }
            helper.setFrom(new InternetAddress(emailProperties.getFromAddress(), emailProperties.getFromName()));
            if (!message.ccAddresses().isEmpty()) {
                helper.setCc(message.ccAddresses().toArray(String[]::new));
            }
            if (message.idempotencyKey() != null && !message.idempotencyKey().isBlank()) {
                mimeMessage.setHeader("X-Idempotency-Key", message.idempotencyKey());
            }
            mailSender.send(mimeMessage);
        }
        catch (MessagingException | java.io.UnsupportedEncodingException exception) {
            throw new EmailSendException("Failed to send email to %s".formatted(message.toAddress()), exception);
        }
    }
}
