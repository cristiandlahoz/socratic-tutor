package com.wornux.security;

import java.util.List;

import com.wornux.data.repositories.identity.AccountRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public CustomUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var account = accountRepository.findByEmail(username)
                .or(() -> accountRepository.findByUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account " + username));

        return new User(account.getEmail(),
                account.getPasswordHash(),
                !account.isLocked(),
                true,
                true,
                true,
                account.isSystemAdmin() ? List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")) : List.of());
    }
}
