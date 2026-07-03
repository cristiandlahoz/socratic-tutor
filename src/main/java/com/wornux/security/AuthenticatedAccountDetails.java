package com.wornux.security;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.TenantAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedAccountDetails implements UserDetails {

    private final Account account;
    private final AppPrincipal principal;

    public AuthenticatedAccountDetails(Account account) {
        this.account = account;
        this.principal = new AppPrincipal(account.getId(), account.getEmail(), account.isLocked());
    }

    public Account account() {
        return account;
    }

    public AppPrincipal principal() {
        return principal;
    }

    public Optional<TenantAccount> currentTenantAccount() {
        return Optional.empty();
    }

    public Optional<GroupClassMember> currentGroupClassMember() {
        return Optional.empty();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return account.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return account.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !account.isLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
