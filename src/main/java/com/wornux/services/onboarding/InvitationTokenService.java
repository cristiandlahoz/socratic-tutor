package com.wornux.services.onboarding;

import java.security.SecureRandom;
import java.util.Base64;

import com.wornux.util.Sha256;
import org.springframework.stereotype.Component;

@Component
public class InvitationTokenService {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateRawToken() {
        var bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        return Sha256.hex(rawToken);
    }
}
