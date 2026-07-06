package com.wornux.services.security;

import java.util.Optional;
import java.util.UUID;

import com.vaadin.flow.server.VaadinSession;
import com.wornux.data.entities.identity.Account;
import com.wornux.security.AuthenticatedAccountDetails;
import com.wornux.security.CustomUserDetailsService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedUserContextUtils {

    private static final String SESSION_DETAILS_KEY = AuthenticatedUserContextUtils.class.getName() + ".details";

    private final CustomUserDetailsService userDetailsService;

    public AuthenticatedUserContextUtils(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    public Optional<AuthenticatedAccountDetails> currentDetails() {
        var authentication = currentAuthentication().orElse(null);
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedAccountDetails details) {
            remember(details);
            return Optional.of(details);
        }

        var sessionDetails = sessionDetails();
        if (sessionDetails.isPresent() && authenticationNameMatches(authentication, sessionDetails.get())) {
            return sessionDetails;
        }

        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return Optional.empty();
        }

        try {
            var loaded = userDetailsService.loadUserByUsername(authentication.getName());
            if (loaded instanceof AuthenticatedAccountDetails details) {
                remember(details);
                return Optional.of(details);
            }
        }
        catch (UsernameNotFoundException _) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public Optional<Account> currentAccount() {
        return currentDetails().map(details -> details.account());
    }

    public Account requireCurrentAccount() {
        return currentAccount().orElseThrow(() -> new IllegalStateException("An authenticated account is required."));
    }

    public Optional<AuthenticatedAccountDetails> refreshCurrentAuthentication(UUID accountId) {
        var refreshed = userDetailsService.loadUserByAccountId(accountId);
        currentAuthentication().ifPresent(
            authentication -> SecurityContextHolder.getContext()
                    .setAuthentication(
                        new UsernamePasswordAuthenticationToken(refreshed,
                                authentication.getCredentials(),
                                refreshed.getAuthorities())));
        remember(refreshed);
        return Optional.of(refreshed);
    }

    private Optional<Authentication> currentAuthentication() {
        var context = SecurityContextHolder.getContext();
        if (context == null || context.getAuthentication() == null) {
            return Optional.empty();
        }
        var authentication = context.getAuthentication();
        if (!authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(authentication);
    }

    private boolean authenticationNameMatches(Authentication authentication, AuthenticatedAccountDetails details) {
        if (authentication == null || authentication.getName() == null) {
            return false;
        }
        var account = details.account();
        return authentication.getName().equalsIgnoreCase(account.getEmail());
    }

    private Optional<AuthenticatedAccountDetails> sessionDetails() {
        var session = VaadinSession.getCurrent();
        if (session == null) {
            return Optional.empty();
        }
        var value = session.getAttribute(SESSION_DETAILS_KEY);
        return value instanceof AuthenticatedAccountDetails details ? Optional.of(details) : Optional.empty();
    }

    private void remember(AuthenticatedAccountDetails details) {
        var session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(SESSION_DETAILS_KEY, details);
        }
    }
}
