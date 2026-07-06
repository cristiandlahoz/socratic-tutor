package com.wornux.services.context;

import java.util.Optional;

import com.wornux.data.repositories.academic.GroupClassMemberRepository;
import com.wornux.data.repositories.identity.AccountContextPreferenceRepository;
import com.wornux.data.repositories.identity.TenantAccountRepository;
import com.wornux.services.security.AuthenticatedUserContextUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActiveAcademicContextResolver {

    private static final String SETUP_REQUIRED_MESSAGE =
            "Academic setup is required before using persisted tutor features.";

    private final AuthenticatedUserContextUtils authenticatedUserContextUtils;
    private final AccountContextPreferenceRepository accountContextPreferenceRepository;
    private final TenantAccountRepository tenantAccountRepository;
    private final GroupClassMemberRepository groupClassMemberRepository;
    private final ActiveAcademicContextResolver self;

    public ActiveAcademicContextResolver(
            AuthenticatedUserContextUtils authenticatedUserContextUtils,
            AccountContextPreferenceRepository accountContextPreferenceRepository,
            TenantAccountRepository tenantAccountRepository,
            GroupClassMemberRepository groupClassMemberRepository,
            @Lazy ActiveAcademicContextResolver self) {
        this.authenticatedUserContextUtils = authenticatedUserContextUtils;
        this.accountContextPreferenceRepository = accountContextPreferenceRepository;
        this.tenantAccountRepository = tenantAccountRepository;
        this.groupClassMemberRepository = groupClassMemberRepository;
        this.self = self;
    }

    @Transactional(readOnly = true)
    public Optional<ActiveAcademicContext> resolveCurrent() {
        return authenticatedUserContextUtils.currentAccount()
                .flatMap(
                    account -> accountContextPreferenceRepository.findById(account.getId())
                            .filter(preference -> preference.getTenant() != null && preference.getGroupClass() != null)
                            .flatMap(preference -> {
                                var tenantAccount = tenantAccountRepository
                                        .findByTenant_IdAndAccount_Id(preference.getTenant().getId(), account.getId())
                                        .orElse(null);
                                if (tenantAccount == null || tenantAccount.isLocked()) {
                                    return Optional.empty();
                                }

                                var groupClassMember =
                                        groupClassMemberRepository
                                                .findByGroupClass_IdAndTenantAccount_Id(
                                                    preference.getGroupClass().getId(),
                                                    tenantAccount.getId())
                                                .orElse(null);
                                if (groupClassMember == null || groupClassMember.isLocked()) {
                                    return Optional.empty();
                                }

                                return Optional.of(
                                    new ActiveAcademicContext(account.getId(),
                                            tenantAccount.getId(),
                                            groupClassMember.getId(),
                                            groupClassMember.getGroupClass().getId(),
                                            groupClassMember.getMemberKind()));
                            }));
    }

    @Transactional(readOnly = true)
    public ActiveAcademicContext requireCurrent() {
        return self.resolveCurrent().orElseThrow(() -> new SetupRequiredException(SETUP_REQUIRED_MESSAGE));
    }
}
