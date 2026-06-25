package com.wornux.services.context;

import java.util.Optional;

import com.wornux.data.entities.identity.Account;
import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.identity.AccountRepository;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActiveAcademicContextResolver {

    private static final String SETUP_REQUIRED_MESSAGE =
            "Academic setup is required before using persisted tutor features.";

    private final AccountRepository accountRepository;
    private final TenantAccountRepository tenantAccountRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;

    public ActiveAcademicContextResolver(
            AccountRepository accountRepository,
            TenantAccountRepository tenantAccountRepository,
            GroupClassMemberRepository groupClassMemberRepository) {
        this.accountRepository = accountRepository;
        this.tenantAccountRepository = tenantAccountRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ActiveAcademicContext> resolveCurrent() {
        var securityContext = SecurityContextHolder.getContext();
        if (securityContext == null || securityContext.getAuthentication() == null) {
            return Optional.empty();
        }

        var authentication = securityContext.getAuthentication();
        if (!authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        return findAccount(authentication.getName()).flatMap(this::toContext);
    }

    @Transactional(readOnly = true)
    public ActiveAcademicContext requireCurrent() {
        return resolveCurrent().orElseThrow(() -> new SetupRequiredException(SETUP_REQUIRED_MESSAGE));
    }

    private Optional<Account> findAccount(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            return Optional.empty();
        }
        var byEmail = accountRepository.findByEmail(principalName);
        return byEmail.isPresent() ? byEmail : accountRepository.findByUsername(principalName);
    }

    private Optional<ActiveAcademicContext> toContext(Account account) {
        if (account.getLastGroupClassMember() == null
                || account.getLastTenantAccount() == null
                || account.getLastGroupClassMember().getGroupClass() == null) {
            return Optional.empty();
        }

        var tenantAccount =
                tenantAccountRepository.findByIdAndAccount_Id(account.getLastTenantAccount().getId(), account.getId())
                        .orElse(null);
        if (tenantAccount == null || tenantAccount.isLocked()) {
            return Optional.empty();
        }

        var groupClassMember = groupClassMemberRepository
                .findByIdAndTenantAccount_Id(account.getLastGroupClassMember().getId(), tenantAccount.getId())
                .orElse(null);
        if (groupClassMember == null
                || groupClassMember.isLocked()
                || groupClassMember.getGroupClass() == null
                || groupClassMember.getGroupClass().getTenant() == null
                || !groupClassMember.getGroupClass().getTenant().getId().equals(tenantAccount.getTenant().getId())) {
            return Optional.empty();
        }

        return Optional.of(
            new ActiveAcademicContext(account.getId(),
                    tenantAccount.getId(),
                    groupClassMember.getId(),
                    groupClassMember.getGroupClass().getId(),
                    groupClassMember.getRole()));
    }
}
