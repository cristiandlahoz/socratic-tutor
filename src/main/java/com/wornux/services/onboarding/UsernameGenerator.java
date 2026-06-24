package com.wornux.services.onboarding;

import java.text.Normalizer;
import java.util.Locale;

import com.wornux.data.repositories.identity.AccountRepository;
import org.springframework.stereotype.Component;

@Component
public class UsernameGenerator {

    private final AccountRepository accountRepository;

    public UsernameGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String generateForEmail(String email) {
        var localPart = email == null || !email.contains("@") ? "user" : email.substring(0, email.indexOf('@'));
        var normalized = Normalizer.normalize(localPart, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        var base = normalized.isBlank() ? "user" : normalized;
        if (!accountRepository.existsByUsername(base)) {
            return base;
        }
        var suffix = 2;
        while (accountRepository.existsByUsername(base + suffix)) {
            suffix++;
        }
        return base + suffix;
    }
}
