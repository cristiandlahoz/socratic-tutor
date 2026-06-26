package com.wornux.config;

import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class EmailConfiguration {

    @Bean
    JavaMailSender javaMailSender(SocraticEmailProperties emailProperties) {
        var sender = new JavaMailSenderImpl();
        sender.setHost(emailProperties.getSmtp().getHost());
        sender.setPort(emailProperties.getSmtp().getPort());
        sender.setUsername(emailProperties.getSmtp().getUsername());
        sender.setPassword(emailProperties.getSmtp().getPassword());

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", Boolean.toString(emailProperties.getSmtp().isAuth()));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(emailProperties.getSmtp().isStarttlsEnabled()));
        properties.put("mail.smtp.ssl.enable", Boolean.toString(emailProperties.getSmtp().isSslEnabled()));
        properties
                .put("mail.smtp.connectiontimeout", Integer.toString(emailProperties.getSmtp().getConnectionTimeout()));
        properties.put("mail.smtp.timeout", Integer.toString(emailProperties.getSmtp().getTimeout()));
        properties.put("mail.smtp.writetimeout", Integer.toString(emailProperties.getSmtp().getWriteTimeout()));
        return sender;
    }
}
