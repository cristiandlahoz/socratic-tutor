package com.wornux.services.ui;

import java.time.Instant;
import com.wornux.data.entities.identity.AccountUiPreferences;
import com.wornux.data.enums.ThemePreference;
import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThemePreferenceService {

    public static final int DEFAULT_BASE_FONT_SIZE = 13;
    private static final int MAX_BASE_FONT_SIZE = 19;

    private final AccountRepository accountRepository;
    private final AuthenticatedUserContextUtils authenticatedUserContextUtils;

    public ThemePreferenceService(
            AccountRepository accountRepository,
            AuthenticatedUserContextUtils authenticatedUserContextUtils) {
        this.accountRepository = accountRepository;
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
    }

    public ThemePreference getThemePreference() {
        var preferences = preferences();
        return preferences.getTheme() == null ? ThemePreference.SYSTEM : preferences.getTheme();
    }

    @Transactional
    public ThemePreference updateThemePreference(ThemePreference preference) {
        var resolved = preference == null ? ThemePreference.SYSTEM : preference;
        var account = authenticatedUserContextUtils.requireCurrentAccount();
        preferences(account.getUiPreferences()).setTheme(resolved);
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);
        return resolved;
    }

    public int getBaseFontSize() {
        var value = preferences().getBaseFontSize();
        return isSupportedBaseFontSize(value) ? value : DEFAULT_BASE_FONT_SIZE;
    }

    @Transactional
    public int updateBaseFontSize(int value) {
        if (!isSupportedBaseFontSize(value)) {
            throw new IllegalArgumentException("Unsupported base font size: " + value);
        }
        var account = authenticatedUserContextUtils.requireCurrentAccount();
        preferences(account.getUiPreferences()).setBaseFontSize(value);
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);
        return value;
    }

    private static boolean isSupportedBaseFontSize(int value) {
        return value >= DEFAULT_BASE_FONT_SIZE && value <= MAX_BASE_FONT_SIZE;
    }

    private AccountUiPreferences preferences() {
        var account = authenticatedUserContextUtils.requireCurrentAccount();
        var preferences = preferences(account.getUiPreferences());
        account.setUiPreferences(preferences);
        return preferences;
    }

    private AccountUiPreferences preferences(AccountUiPreferences preferences) {
        return preferences == null ? new AccountUiPreferences() : preferences;
    }
}
