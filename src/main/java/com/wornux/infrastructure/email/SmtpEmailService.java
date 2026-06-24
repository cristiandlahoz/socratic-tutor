package com.wornux.infrastructure.email;

import com.wornux.config.SocraticEmailProperties;
import com.wornux.services.email.EmailMessage;
import com.wornux.services.email.EmailSendException;
import com.wornux.services.email.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final SocraticEmailProperties emailProperties;

    public SmtpEmailService(JavaMailSender mailSender, SocraticEmailProperties emailProperties) {
        this.mailSender = mailSender;
        this.emailProperties = emailProperties;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(message.toAddress());
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);
            helper.setFrom(new InternetAddress(emailProperties.getFromAddress(), emailProperties.getFromName()));
            if (!message.ccAddresses().isEmpty()) {
                helper.setCc(message.ccAddresses().toArray(String[]::new));
            }
            mailSender.send(mimeMessage);
        }
        catch (MessagingException | java.io.UnsupportedEncodingException exception) {
            throw new EmailSendException("Failed to send email to %s".formatted(message.toAddress()), exception);
        }
    }
}
