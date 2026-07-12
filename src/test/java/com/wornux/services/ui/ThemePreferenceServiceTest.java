package com.wornux.services.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.wornux.data.entities.identity.Account;
import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import org.junit.jupiter.api.Test;

class ThemePreferenceServiceTest {

    @Test
    void storesOnlySupportedBaseFontSizes() {
        var account = new Account();
        account.setId(UUID.randomUUID());
        var repository = mock(AccountRepository.class);
        var authenticatedUser = mock(AuthenticatedUserContextUtils.class);
        when(authenticatedUser.requireCurrentAccount()).thenReturn(account);
        var service = new ThemePreferenceService(repository, authenticatedUser);

        assertThat(service.updateBaseFontSize(16)).isEqualTo(16);
        assertThat(account.getUiPreferences().getBaseFontSize()).isEqualTo(16);
        verify(repository).save(account);
        assertThatThrownBy(() -> service.updateBaseFontSize(12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateBaseFontSize(20))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
