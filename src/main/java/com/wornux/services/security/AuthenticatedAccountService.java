package com.wornux.services.security;

import java.util.Optional;

import com.wornux.data.entities.identity.Account;
import com.wornux.data.repositories.identity.AccountRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedAccountService {

    private final AccountRepository accountRepository;

    public AuthenticatedAccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Account> currentAccount() {
        var context = SecurityContextHolder.getContext();
        if (context == null || context.getAuthentication() == null) {
            return Optional.empty();
        }
        var authentication = context.getAuthentication();
        if (!authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return accountRepository.findByEmail(authentication.getName())
                .or(() -> accountRepository.findByUsername(authentication.getName()));
    }

    @Transactional(readOnly = true)
    public Account requireCurrentAccount() {
        return currentAccount().orElseThrow(() -> new IllegalStateException("An authenticated account is required."));
    }
}
