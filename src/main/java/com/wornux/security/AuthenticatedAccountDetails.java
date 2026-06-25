package com.wornux.security;

import java.util.Collection;
import java.util.Optional;

import com.wornux.data.entities.academic.GroupClassMember;
import com.wornux.data.entities.identity.Account;
import com.wornux.data.entities.identity.TenantAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedAccountDetails implements UserDetails {

    private final Account account;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedAccountDetails(Account account, Collection<? extends GrantedAuthority> authorities) {
        this.account = account;
        this.authorities = authorities;
    }

    public Account account() {
        return account;
    }

    public Optional<TenantAccount> currentTenantAccount() {
        return Optional.ofNullable(account.getLastTenantAccount());
    }

    public Optional<GroupClassMember> currentGroupClassMember() {
        return Optional.ofNullable(account.getLastGroupClassMember());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
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
