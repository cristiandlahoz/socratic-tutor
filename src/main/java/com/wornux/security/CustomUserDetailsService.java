package com.wornux.security;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.wornux.data.entities.academic.GroupClassMemberRole;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.authorization.TenantAccountRoleRepository;
import com.wornux.data.repositories.identity.AccountRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;
    private final TenantAccountRoleRepository tenantAccountRoleRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;

    public CustomUserDetailsService(
            AccountRepository accountRepository,
            TenantAccountRoleRepository tenantAccountRoleRepository,
            GroupClassMemberRepository groupClassMemberRepository) {
        this.accountRepository = accountRepository;
        this.tenantAccountRoleRepository = tenantAccountRoleRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var account = accountRepository.findByEmail(username)
                .or(() -> accountRepository.findByUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account %s".formatted(username)));

        return toUserDetails(account);
    }

    @Transactional(readOnly = true)
    public AuthenticatedAccountDetails loadUserByAccountId(UUID accountId) {
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account %s".formatted(accountId)));
        return toUserDetails(account);
    }

    private AuthenticatedAccountDetails toUserDetails(Account account) {
        return new AuthenticatedAccountDetails(account, buildAuthorities(account));
    }

    private Set<GrantedAuthority> buildAuthorities(Account account) {
        var authorities = new LinkedHashSet<GrantedAuthority>();
        if (account.isSystemAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
        }

        tenantAccountRoleRepository.findByTenantAccount_Account_IdAndTenantAccount_LockedFalse(account.getId())
                .stream()
                .filter(role -> role.getRole() != null && role.getRole().isActive())
                .map(role -> role.getRole().getCode())
                .filter(code -> code != null && !code.isBlank())
                .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                .forEach(authorities::add);

        groupClassMemberRepository.findByTenantAccount_Account_IdAndLockedFalseOrderByJoinedAtAsc(account.getId())
                .stream()
                .map(member -> member.getRole() == GroupClassMemberRole.PROFESSOR ? "ROLE_PROFESSOR" : "ROLE_STUDENT")
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        return authorities;
    }
}
