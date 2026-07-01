package com.wornux.services.context;

import java.util.Optional;

import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import com.wornux.services.security.AuthenticatedUserContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActiveAcademicContextResolver {

    private static final String SETUP_REQUIRED_MESSAGE =
            "Academic setup is required before using persisted tutor features.";

    private final AuthenticatedUserContext authenticatedUserContext;
    private final TenantAccountRepository tenantAccountRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;
    private final ActiveAcademicContextResolver self;

    public ActiveAcademicContextResolver(
            AuthenticatedUserContext authenticatedUserContext,
            TenantAccountRepository tenantAccountRepository,
            GroupClassMemberRepository groupClassMemberRepository,
            @Lazy ActiveAcademicContextResolver self) {
        this.authenticatedUserContext = authenticatedUserContext;
        this.tenantAccountRepository = tenantAccountRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
        this.self = self;
    }

    @Transactional(readOnly = true)
    public Optional<ActiveAcademicContext> resolveCurrent() {
        return authenticatedUserContext.currentAccount().flatMap(account -> {
            if (account.getLastGroupClassMember() == null
                    || account.getLastTenantAccount() == null
                    || account.getLastGroupClassMember().getGroupClass() == null) {
                return Optional.empty();
            }

            var tenantAccount = tenantAccountRepository
                    .findByIdAndAccount_Id(account.getLastTenantAccount().getId(), account.getId())
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
        });
    }

    @Transactional(readOnly = true)
    public ActiveAcademicContext requireCurrent() {
        return self.resolveCurrent().orElseThrow(() -> new SetupRequiredException(SETUP_REQUIRED_MESSAGE));
    }
}
