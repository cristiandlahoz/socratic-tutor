package com.wornux.services.security;

import java.util.Optional;

import com.wornux.data.entities.identity.Account;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedAccountService {

    private final AuthenticatedUserContext authenticatedUserContext;

    public AuthenticatedAccountService(AuthenticatedUserContext authenticatedUserContext) {
        this.authenticatedUserContext = authenticatedUserContext;
    }

    @Transactional(readOnly = true)
    public Optional<Account> currentAccount() {
        return authenticatedUserContext.currentAccount();
    }

    @Transactional(readOnly = true)
    public Account requireCurrentAccount() {
        return authenticatedUserContext.requireCurrentAccount();
    }
}
