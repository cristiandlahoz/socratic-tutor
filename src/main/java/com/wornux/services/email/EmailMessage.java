package com.wornux.services.email;

import java.util.List;

public record EmailMessage(
        String toAddress, String subject, String plainTextBody, String htmlBody, List<String> ccAddresses) {

    public EmailMessage {
        ccAddresses = ccAddresses == null ? List.of() : List.copyOf(ccAddresses);
    }

    public EmailMessage(String toAddress, String subject, String plainTextBody, String htmlBody) {
        this(toAddress, subject, plainTextBody, htmlBody, List.of());
    }

    public EmailMessage(String toAddress, String subject, String htmlBody) {
        this(toAddress, subject, null, htmlBody, List.of());
    }
}
