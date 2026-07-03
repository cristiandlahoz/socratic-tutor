package com.wornux.security;

import java.util.UUID;

import com.wornux.data.entities.identity.Account;
import com.wornux.data.repositories.identity.AccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public CustomUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account %s".formatted(email)));

        return toUserDetails(account);
    }

    @Transactional(readOnly = true)
    public AuthenticatedAccountDetails loadUserByAccountId(UUID accountId) {
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account %s".formatted(accountId)));
        return toUserDetails(account);
    }

    private AuthenticatedAccountDetails toUserDetails(Account account) {
        return new AuthenticatedAccountDetails(account);
    }
}
